package com.insurance.renewal.service;

import com.insurance.renewal.dto.Policy360ViewDTO;
import com.insurance.renewal.entity.*;
import com.insurance.renewal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyMasterRecordRepository policyRepo;
    private final ClaimRepository claimRepo;
    private final RenewalProcessingRepository processingRepo;
    private final RenewalCriteriaResultRepository criteriaResultRepo;
    private final UnderwriterActionRepository underwriterActionRepo;
    private final NotificationLogRepository notificationLogRepo;
    private final PaymentTransactionRepository paymentRepo;

    /** Builds the full "Policy 360 View" payload - the single-screen aggregate
     *  used by Underwriter, Branch Manager, Agent and Customer UIs alike. */
    public Policy360ViewDTO get360View(String policyNo) {
        PolicyMasterRecord policy = policyRepo.findByPolicyNo(policyNo)
                .orElseThrow(() -> new NoSuchElementException("Policy not found: " + policyNo));

        List<Policy360ViewDTO.ClaimSummary> claims = claimRepo.findByPolicy_PolicyId(policy.getPolicyId())
                .stream()
                .map(c -> Policy360ViewDTO.ClaimSummary.builder()
                        .claimNo(c.getClaimNo())
                        .dateOfLoss(c.getDateOfLoss())
                        .perilType(c.getPerilType())
                        .amountClaimed(c.getAmountClaimed())
                        .amountPaid(c.getAmountPaid())
                        .claimStatus(c.getClaimStatus().name())
                        .build())
                .toList();

        List<RenewalProcessing> processingHistory =
                processingRepo.findByPolicy_PolicyIdOrderByBatchRunDateDesc(policy.getPolicyId());

        Policy360ViewDTO.RenewalDecisionSummary decisionSummary = null;
        List<Policy360ViewDTO.NotificationSummary> notifications = List.of();
        List<Policy360ViewDTO.PaymentSummary> payments = List.of();

        if (!processingHistory.isEmpty()) {
            RenewalProcessing latest = processingHistory.get(0);

            List<Policy360ViewDTO.CriteriaResultSummary> breakdown =
                    criteriaResultRepo.findByProcessing_ProcessingId(latest.getProcessingId())
                            .stream()
                            .map(r -> Policy360ViewDTO.CriteriaResultSummary.builder()
                                    .criteriaName(r.getCriteria().getCriteriaName())
                                    .matchedBandLabel(r.getMatchedBandLabel())
                                    .impactPctApplied(r.getImpactPctApplied())
                                    .forceActionFlag(r.getForceActionFlag())
                                    .build())
                            .toList();

            Policy360ViewDTO.UnderwriterActionSummary uwAction = underwriterActionRepo
                    .findByProcessing_ProcessingId(latest.getProcessingId())
                    .stream()
                    .max(Comparator.comparing(UnderwriterAction::getActionDate))
                    .map(a -> Policy360ViewDTO.UnderwriterActionSummary.builder()
                            .underwriterName(a.getUserId() == null ? null : String.valueOf(a.getUserId()))
                            .actionTaken(a.getActionTaken().name())
                            .revisedLoadingDiscountPct(a.getRevisedLoadingDiscountPct())
                            .remarks(a.getRemarks())
                            .actionDate(a.getActionDate())
                            .build())
                    .orElse(null);

            decisionSummary = Policy360ViewDTO.RenewalDecisionSummary.builder()
                    .processingId(latest.getProcessingId())
                    .ruleSetVersion(latest.getRuleSet().getVersionCode())
                    .batchRunDate(latest.getBatchRunDate())
                    .computedClaimCount(latest.getComputedClaimCount())
                    .computedTotalClaimAmount(latest.getComputedTotalClaimAmount())
                    .computedLossRatio(latest.getComputedLossRatio())
                    .netLoadingDiscountPct(latest.getNetLoadingDiscountPct())
                    .finalPremiumAmount(latest.getFinalPremiumAmount())
                    .resolutionStatus(latest.getResolutionStatus().name())
                    .forceActionTriggered(latest.getForceActionTriggered())
                    .criteriaBreakdown(breakdown)
                    .underwriterAction(uwAction)
                    .build();

            notifications = notificationLogRepo.findByProcessing_ProcessingId(latest.getProcessingId())
                    .stream()
                    .map(n -> Policy360ViewDTO.NotificationSummary.builder()
                            .channel(n.getChannel().name())
                            .dayOffset(n.getDayOffset())
                            .sentAt(n.getSentAt())
                            .deliveryStatus(n.getDeliveryStatus().name())
                            .build())
                    .toList();
        }

        payments = paymentRepo.findByPolicy_PolicyId(policy.getPolicyId())
                .stream()
                .map(p -> Policy360ViewDTO.PaymentSummary.builder()
                        .amountPaid(p.getAmountPaid())
                        .paymentMode(p.getPaymentMode().name())
                        .paymentStatus(p.getPaymentStatus().name())
                        .transactionDate(p.getTransactionDate())
                        .build())
                .toList();

        return Policy360ViewDTO.builder()
                .policyId(policy.getPolicyId())
                .policyNo(policy.getPolicyNo())
                .productName(policy.getProductName())
                .customerName(policy.getCustomerName())
                .customerMobile(policy.getMobileNo())
                .customerEmail(policy.getEmail())
                .branchName(policy.getBranchName())
                .agentName(policy.getAgentDisplayName())
                .sumInsured(policy.getSumInsured())
                .policyStartDate(policy.getPolicyStartDate())
                .policyExpiryDate(policy.getPolicyExpiryDate())
                .policyStatus(policy.getPolicyStatus())
                .vehicleRegNo(policy.getVehicleRegNo())
                .vehicleMfgDate(policy.getVehicleMfgDate())
                .vehicleMfgCountry(policy.getVehicleMfgCountry())
                .driverDob(policy.getDriverDob())
                .claims(claims)
                .latestRenewalDecision(decisionSummary)
                .notifications(notifications)
                .payments(payments)
                .build();
    }
}
