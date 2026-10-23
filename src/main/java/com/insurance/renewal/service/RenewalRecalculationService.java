package com.insurance.renewal.service;

import com.insurance.renewal.entity.PolicyMasterRecord;
import com.insurance.renewal.entity.RenewalProcessing;
import com.insurance.renewal.repository.PolicyMasterRecordRepository;
import com.insurance.renewal.repository.RenewalProcessingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * "Recalculate" button of the Renewal Decision screen.
 *
 * 1. calls EwayCommonApi /common/premium-calc for the policy,
 * 2. adds up the covers that count towards the overall premium (see {@link #countsTowardsOverall}),
 * 3. updates policy_master.overall_premium_lc / overall_premium_fc of that policy no,
 * 4. re-runs the renewal batch for that one policy, so renewal_processing is built from the updated policy_master.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RenewalRecalculationService {

    private final PolicyMasterRecordRepository policyRepo;
    private final RenewalProcessingRepository processingRepo;
    private final HomePositionSyncService homePositionSyncService;
    private final RenewalBatchService batchService;

    public Map<String, Object> recalculate(Long policyId, String policyNo) {
        PolicyMasterRecord policy = (policyId != null
                ? policyRepo.findById(policyId)
                : policyRepo.findByPolicyNo(policyNo))
                .orElseThrow(() -> new NoSuchElementException(
                        "Policy not found: " + (policyId != null ? "policy_id=" + policyId : policyNo)));

        BigDecimal oldLc = policy.getOverallPremiumLc();
        BigDecimal oldFc = policy.getOverallPremiumFc();

        List<Map<String, Object>> covers = homePositionSyncService.fetchCoverListForPolicy(policy);

        BigDecimal fc = BigDecimal.ZERO;
        BigDecimal lc = BigDecimal.ZERO;
        List<Map<String, Object>> used = new ArrayList<>();
        for (Map<String, Object> c : covers) {
            if (!countsTowardsOverall(c)) continue;
            BigDecimal cFc = num(c.get("PremiumIncludedTax"));
            BigDecimal cLc = num(c.get("PremiumIncludedTaxLC"));
            fc = fc.add(cFc);
            lc = lc.add(cLc);
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("coverId", c.get("CoverId"));
            u.put("coverName", c.get("CoverName"));
            u.put("coverageType", c.get("CoverageType"));
            u.put("userOpt", c.get("UserOpt"));
            u.put("isSelected", c.get("isSelected"));
            u.put("premiumIncludedTax", cFc);
            u.put("premiumIncludedTaxLC", cLc);
            used.add(u);
        }
        if (used.isEmpty())
            throw new IllegalStateException("No cover matched the overall-premium rules; policy_master was not changed");

        // 3. update policy_master of this policy no
        policy.setOverallPremiumFc(fc.setScale(2, RoundingMode.HALF_UP));
        policy.setOverallPremiumLc(lc.setScale(2, RoundingMode.HALF_UP));
        policyRepo.save(policy);
        log.info("Recalculate {}: overall premium LC {} -> {}, FC {} -> {}",
                policy.getPolicyNo(), oldLc, policy.getOverallPremiumLc(), oldFc, policy.getOverallPremiumFc());

        // 4. same batch process, for this policy only
        boolean processed = batchService.reprocessPolicy(policy, LocalDate.now());

        List<RenewalProcessing> latest = processingRepo.findByPolicy_PolicyIdOrderByBatchRunDateDesc(policy.getPolicyId());

        // 5. final premium on policy_master = overall premium x (1 + net loading% / discount%).
        // When the batch processed the policy it has already stored it; when it skipped the policy
        // (RENEWED / BUSINESS_LOST / CLOSED) apply the net % of the latest processing record here.
        if (!processed) {
            BigDecimal pct = latest.isEmpty() || latest.get(0).getNetLoadingDiscountPct() == null
                    ? BigDecimal.ZERO : latest.get(0).getNetLoadingDiscountPct();
            BigDecimal factor = BigDecimal.ONE.add(pct.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            policy.setFinalPremiumLc(policy.getOverallPremiumLc().multiply(factor).setScale(2, RoundingMode.HALF_UP));
            policy.setFinalPremiumFc(policy.getOverallPremiumFc().multiply(factor).setScale(2, RoundingMode.HALF_UP));
            policyRepo.save(policy);
        }
        log.info("Recalculate {}: final premium LC {} FC {}", policy.getPolicyNo(),
                policy.getFinalPremiumLc(), policy.getFinalPremiumFc());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("policyId", policy.getPolicyId());
        out.put("policyNo", policy.getPolicyNo());
        out.put("oldOverallPremiumLC", oldLc);
        out.put("oldOverallPremiumFC", oldFc);
        out.put("newOverallPremiumLC", policy.getOverallPremiumLc());
        out.put("newOverallPremiumFC", policy.getOverallPremiumFc());
        out.put("newFinalPremiumLC", policy.getFinalPremiumLc());
        out.put("newFinalPremiumFC", policy.getFinalPremiumFc());
        out.put("coversUsed", used);
        out.put("batchProcessed", processed);
        if (!processed) out.put("batchMessage", "Batch skipped this policy (renewal already RENEWED / BUSINESS_LOST or due CLOSED)");
        if (!latest.isEmpty()) {
            RenewalProcessing p = latest.get(0);
            out.put("processingId", p.getProcessingId());
            out.put("resolutionStatus", p.getResolutionStatus());
            out.put("netLoadingDiscountPct", p.getNetLoadingDiscountPct());
            out.put("finalPremiumAmount", p.getFinalPremiumAmount());
        }
        return out;
    }

    /**
     * Which premium-calc covers make up the overall premium:
     *  - CoverageType 'B' (base cover): always;
     *  - CoverageType 'O' with UserOpt 'Y' and isSelected 'N';
     *  - CoverageType 'O' with isSelected 'Y' and UserOpt empty (null).
     */
    static boolean countsTowardsOverall(Map<String, Object> c) {
        String type = str(c.get("CoverageType"));
        String userOpt = str(c.get("UserOpt"));
        String selected = str(c.get("isSelected"));
        if ("B".equalsIgnoreCase(type)) return true;
        if (!"O".equalsIgnoreCase(type)) return false;
        if ("Y".equalsIgnoreCase(userOpt) && "N".equalsIgnoreCase(selected)) return true;
        return "Y".equalsIgnoreCase(selected) && userOpt.isEmpty();
    }

    private static String str(Object o) { return o == null ? "" : o.toString().trim(); }

    private static BigDecimal num(Object o) {
        if (o == null) return BigDecimal.ZERO;
        try { return new BigDecimal(o.toString()); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
