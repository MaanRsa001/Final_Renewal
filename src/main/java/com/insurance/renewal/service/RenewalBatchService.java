package com.insurance.renewal.service;

import com.insurance.renewal.entity.*;
import com.insurance.renewal.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * The daily renewal batch: scans policies expiring within the next 30 days,
 * evaluates the product's active rule set against each policy's claim
 * experience and policy attributes, and writes a RenewalProcessing decision
 * + per-criterion breakdown.
 *
 * Every criterion's value is resolved generically by RenewalCriteriaValueResolver
 * from the field it declares on mst_criteria (source_entity/source_field/
 * aggregation) - there is deliberately no switch on criteria_code here. That
 * mapping is what Master Setup > Applicable Criteria > "Define bands" edits,
 * so a new criterion built on an already-exposed policy_master or claims
 * field needs zero changes to this class.
 *
 * Scheduled for 06:00 every day - "batch created every morning" per the
 * original requirement. Can also be triggered on demand via
 * POST /api/renewal-batch/run (see BatchController) for the Underwriter's
 * "Batch renewal processing" screen action.
 *
 * Delete-and-insert rule:
 *   Policies whose latest RenewalProcessing has resolutionStatus RENEWED or
 *   BUSINESS_LOST are considered terminal — their outcome is already decided
 *   and must not be disturbed. The batch skips delete+insert for those records
 *   and counts them in the "skipped" bucket. All other statuses
 *   (AUTO_RENEWAL_ELIGIBLE, REFERRAL_PENDING, REJECTED) are re-evaluated
 *   fresh on every run.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RenewalBatchService {

    /**
     * Terminal statuses — existing processing records with these statuses
     * are preserved as-is; the batch will not delete+re-insert them.
     */
    private static final Set<RenewalProcessing.ResolutionStatus> TERMINAL_STATUSES =
            EnumSet.of(
                RenewalProcessing.ResolutionStatus.RENEWED,
                RenewalProcessing.ResolutionStatus.BUSINESS_LOST
            );

    private final PolicyMasterRecordRepository policyRepo;
    private final ClaimRepository claimRepo;
    private final RenewalDueRepository renewalDueRepo;
    private final RenewalProcessingRepository processingRepo;
    private final RenewalCriteriaResultRepository criteriaResultRepo;
    private final RuleSetRepository ruleSetRepo;
    private final RuleDefinitionRepository ruleDefinitionRepo;
    private final MstLossRatioConfigRepository lossRatioConfigRepo;
    private final RenewalCriteriaValueResolver valueResolver;
    private final NotificationLogRepository notificationLogRepo;
    private final PaymentTransactionRepository paymentTransactionRepo;
    private final UnderwriterActionRepository underwriterActionRepo;
    private final EwayNotificationService ewayNotificationService;   // renewal mail via eway /pushnotification

    /** Result DTO returned by runBatch — carries both processed and skipped counts. */
    public record BatchResult(int processed, int skipped, int total) {}


    /**
     * Main batch entry point.
     *
     * @return BatchResult with:
     *   processed = policies that were deleted + re-inserted this run
     *   skipped   = policies whose existing record is RENEWED or BUSINESS_LOST (left untouched)
     *   total     = processed + skipped (full window size)
     */
    @Transactional
    public BatchResult runBatch(LocalDate runDate) {
        LocalDate windowEnd = runDate.plusDays(30);
        int processedCount = 0;
        int skippedCount   = 0;

        List<PolicyMasterRecord> expiringPolicies = policyRepo.findAll().stream()
                .filter(p -> p.getPolicyExpiryDate() != null
                        && !p.getPolicyExpiryDate().isBefore(runDate) && !p.getPolicyExpiryDate().isAfter(windowEnd))
                .filter(p -> PolicyMasterRecord.PolicyStatus.ACTIVE.name().equalsIgnoreCase(p.getPolicyStatus()))
                .toList();

        for (PolicyMasterRecord policy : expiringPolicies) {
            if (processOnePolicy(policy, runDate, true)) processedCount++; else skippedCount++;
        }

        int total = processedCount + skippedCount;
        log.info("Renewal batch complete — processed: {}, skipped (terminal): {}, total: {}",
                processedCount, skippedCount, total);
        return new BatchResult(processedCount, skippedCount, total);
    }

    /**
     * Recalculation entry point: re-runs the renewal evaluation for ONE policy (same steps as the batch:
     * renewal_due, delete old non-terminal processing, evaluate rules, insert fresh processing).
     * @return true when processed, false when skipped (CLOSED due, or RENEWED / BUSINESS_LOST already)
     */
    @Transactional
    public boolean reprocessPolicy(PolicyMasterRecord policy, LocalDate runDate) {
        return processOnePolicy(policy, runDate, false);
    }

    /** One policy of the batch loop. true = processed, false = skipped. */
    private boolean processOnePolicy(PolicyMasterRecord policy, LocalDate runDate, boolean sendMail) {
            RenewalDue due = renewalDueRepo.findByPolicy_PolicyId(policy.getPolicyId()).stream()
                    .filter(d -> d.getExpiryDate().equals(policy.getPolicyExpiryDate()))
                    .findFirst()
                    .orElseGet(() -> renewalDueRepo.save(RenewalDue.builder()
                            .policy(policy)
                            .policyNo(policy.getPolicyNo())
                            .expiryDate(policy.getPolicyExpiryDate())
                            .daysToExpiry((int) Period.between(runDate, policy.getPolicyExpiryDate()).getDays())
                            .currentStatus(RenewalDue.Status.DUE)
                            .batchFirstPickedDate(runDate)
                            .build()));

            if (due.getCurrentStatus() == RenewalDue.Status.CLOSED) return false;

            // --- Terminal-status guard ---
            // If the most recent processing record for this policy is RENEWED or
            // BUSINESS_LOST, skip delete+insert entirely and count it as skipped.
            List<RenewalProcessing> existingList =
                    processingRepo.findByPolicy_PolicyIdOrderByBatchRunDateDesc(policy.getPolicyId());

            if (!existingList.isEmpty()) {
                RenewalProcessing latest = existingList.get(0);
                if (TERMINAL_STATUSES.contains(latest.getResolutionStatus())) {
                    log.info("Policy {} is {} — skipping delete+insert",
                            policy.getPolicyNo(), latest.getResolutionStatus());
                    return false; // leave the record exactly as it is
                }
            }
            // --- End guard ---

            due.setDaysToExpiry((int) (policy.getPolicyExpiryDate().toEpochDay() - runDate.toEpochDay()));
            due.setBatchLastProcessedDate(runDate);
            due.setPolicyNo(policy.getPolicyNo());   // also back-fills older renewal_due rows
            due.setCurrentStatus(RenewalDue.Status.IN_PROGRESS);
            renewalDueRepo.save(due);

            evaluatePolicy(policy, due, existingList, runDate);

            // batch run only: policy pulled + premium calculated -> ask eway to send the renewal mail
            if (sendMail) {
                RenewalProcessing latestProcessing = processingRepo
                        .findByPolicy_PolicyIdOrderByBatchRunDateDesc(policy.getPolicyId()).stream().findFirst().orElse(null);
                ewayNotificationService.sendRenewalMail(policy, latestProcessing);
            }
            return true;
    }

    /**
     * Delete existing (non-terminal) processing records for the policy, then
     * evaluate fresh and insert a new RenewalProcessing + criteria results.
     *
     * @param existingList already-fetched processing records (avoids a second DB hit)
     */
    private void evaluatePolicy(PolicyMasterRecord policy, RenewalDue due,
                                List<RenewalProcessing> existingList, LocalDate runDate) {

        if (!existingList.isEmpty()) {
            existingList.forEach(p -> {
                // child 1 — criteria results
                criteriaResultRepo.deleteByProcessing_ProcessingId(p.getProcessingId());

                // child 2 — notification logs
                notificationLogRepo.deleteByProcessing_ProcessingId(p.getProcessingId());

                // child 3 — payment transactions
                paymentTransactionRepo.deleteByProcessing_ProcessingId(p.getProcessingId());

                // child 4 — underwriter actions
                underwriterActionRepo.deleteByProcessing_ProcessingId(p.getProcessingId());

                // parent — processing record
                processingRepo.delete(p);
            });
            log.info("Deleted {} existing processing record(s) for {} — inserting fresh",
                    existingList.size(), policy.getPolicyNo());
        }

        RuleSet ruleSet = ruleSetRepo
                .findFirstByProductIdAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByEffectiveFromDesc(
                        policy.getProductId(), RuleSet.Status.ACTIVE, runDate, runDate)
                .orElseThrow(() -> new NoSuchElementException(
                        "No ACTIVE rule set found for product " + policy.getProductId()
                                + (policy.getProductName() != null ? " (" + policy.getProductName() + ")" : "") + " on " + runDate));

        List<RuleDefinition> rules = ruleDefinitionRepo.findByRuleSet_RuleSetId(ruleSet.getRuleSetId());

        LossRatioConfig lossRatioConfig = lossRatioConfigRepo.findByProductId(policy.getProductId())
                .stream().findFirst().orElse(null);
        int lookbackYears = lossRatioConfig != null ? lossRatioConfig.getClaimLookbackYears() : 3;
        LocalDate lookbackStart = runDate.minusYears(lookbackYears);

        List<Claim> claimsInWindow = claimRepo.findByPolicy_PolicyId(policy.getPolicyId()).stream()
                .filter(c -> !c.getDateOfLoss().isBefore(lookbackStart))
                .toList();

        int claimCount = claimsInWindow.size();
        BigDecimal totalClaimAmount = claimsInWindow.stream()
                .map(Claim::getAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add);

        String basis = lossRatioConfig != null ? lossRatioConfig.getBasisType().name() : "PREMIUM_PAID";
        BigDecimal base = "SUM_INSURED".equals(basis) ? policy.getSumInsured() : policy.getPreviousPremium();
        if (base == null) base = BigDecimal.ZERO;
        BigDecimal lossRatio = base.compareTo(BigDecimal.ZERO) > 0
                ? totalClaimAmount.multiply(BigDecimal.valueOf(100)).divide(base, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, BigDecimal> computedValues = Map.of("LOSS_RATIO", lossRatio);

        BigDecimal netPct = BigDecimal.ZERO;
        String forceAction = null;
        List<RenewalCriteriaResult> firedResults = new ArrayList<>();

        for (RuleDefinition rule : rules) {
            if (!rule.getIsActive()) continue;
            Criteria criteria = rule.getCriteria();

            boolean matched;
            if (criteria.getDataType() == Criteria.DataType.ENUM) {
                String value = valueResolver.resolveEnum(criteria, policy);
                matched = rule.getEnumValue() != null && rule.getEnumValue().equalsIgnoreCase(value);
            } else {
                BigDecimal value = valueResolver.resolveNumeric(criteria, policy, claimsInWindow, runDate, computedValues);
                matched = value != null && inRange(value, rule);
            }
            if (!matched) continue;

            netPct = netPct.add(rule.getImpactPct());
            if (rule.getForceAction() != RuleDefinition.ForceAction.NONE) {
                forceAction = rule.getForceAction().name();
            }

            firedResults.add(RenewalCriteriaResult.builder()
                    .criteria(criteria)
                    .criteriaName(criteria.getCriteriaName())
                    .matchedBandLabel(describeMatch(rule))
                    .impactPctApplied(rule.getImpactPct())
                    .forceActionFlag(rule.getForceAction() == RuleDefinition.ForceAction.NONE ? null : rule.getForceAction().name())
                    .build());
        }

        if (ruleSet.getMaxLoadingCapPct() != null && netPct.compareTo(ruleSet.getMaxLoadingCapPct()) > 0) {
            netPct = ruleSet.getMaxLoadingCapPct();
        }
        if (ruleSet.getMaxDiscountCapPct() != null && netPct.compareTo(ruleSet.getMaxDiscountCapPct().negate()) < 0) {
            netPct = ruleSet.getMaxDiscountCapPct().negate();
        }

        RenewalProcessing.ResolutionStatus status;
        if ("FORCE_REJECT".equals(forceAction)) {
            status = RenewalProcessing.ResolutionStatus.REJECTED;
        } else if ("FORCE_REFER".equals(forceAction)) {
            status = RenewalProcessing.ResolutionStatus.REFERRAL_PENDING;
        } else {
            status = RenewalProcessing.ResolutionStatus.AUTO_RENEWAL_ELIGIBLE;
        }

        BigDecimal previousPremium = policy.getPreviousPremium() != null ? policy.getPreviousPremium() : BigDecimal.ZERO;
        BigDecimal finalPremium = previousPremium
                .multiply(BigDecimal.ONE.add(netPct.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);

        // store the final premium (overall x (1 + net loading% / discount%)) on policy_master as well
        BigDecimal factor = BigDecimal.ONE.add(netPct.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        BigDecimal overallFc = policy.getOverallPremiumFc() != null ? policy.getOverallPremiumFc() : policy.getPreviousPremium();
        policy.setFinalPremiumLc(finalPremium);
        policy.setFinalPremiumFc(overallFc == null ? finalPremium : overallFc.multiply(factor).setScale(2, RoundingMode.HALF_UP));
        policyRepo.save(policy);

        RenewalProcessing processing = processingRepo.save(RenewalProcessing.builder()
                .renewalDue(due)
                .policy(policy)
                .policyNo(policy.getPolicyNo())
                .ruleSet(ruleSet)
                .batchRunDate(runDate)
                .computedClaimCount(claimCount)
                .computedTotalClaimAmount(totalClaimAmount)
                .computedLossRatio(lossRatio)
                .netLoadingDiscountPct(netPct)
                .finalPremiumAmount(finalPremium)
                .resolutionStatus(status)
                .forceActionTriggered(forceAction)
                .processedAt(LocalDateTime.now())
                .build());

        firedResults.forEach(r -> { r.setProcessing(processing); criteriaResultRepo.save(r); });
    }

    /** Inclusive on both bounds - a value equal to rangeFrom or rangeTo matches. */
    private boolean inRange(BigDecimal value, RuleDefinition rule) {
        if (!rule.isRangeRule()) return false;
        return value.compareTo(rule.getRangeFrom()) >= 0 && value.compareTo(rule.getRangeTo()) <= 0;
    }

    /** Human-readable label stored on the decision log so the 360 view can explain the match. */
    private String describeMatch(RuleDefinition rule) {
        if (rule.isRangeRule()) {
            return rule.getRangeFrom().stripTrailingZeros().toPlainString()
                 + " - " + rule.getRangeTo().stripTrailingZeros().toPlainString();
        }
        return rule.getEnumValue();
    }
}
