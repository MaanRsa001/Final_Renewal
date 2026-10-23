package com.insurance.renewal.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Single aggregate payload for the "Policy 360 View" screen - shared across
 * Underwriter, Branch Manager, Agent and Customer UIs. Field-level visibility
 * per role is handled on the Angular side (see shared/policy-360-view).
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Policy360ViewDTO {

    // ---- Policy summary ----
    private Long policyId;
    private String policyNo;
    private String productName;
    private String lobType;
    private String customerName;
    private String customerMobile;
    private String customerEmail;
    private String branchName;
    private String agentName;
    private BigDecimal sumInsured;
    private LocalDate policyStartDate;
    private LocalDate policyExpiryDate;
    private String policyStatus;

    // motor-specific (null for non-motor)
    private String vehicleRegNo;
    private LocalDate vehicleMfgDate;
    private String vehicleMfgCountry;
    private LocalDate driverDob;

    // ---- Claims ----
    private List<ClaimSummary> claims;

    // ---- Latest renewal decision ----
    private RenewalDecisionSummary latestRenewalDecision;

    // ---- Notification history ----
    private List<NotificationSummary> notifications;

    // ---- Payment history ----
    private List<PaymentSummary> payments;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ClaimSummary {
        private String claimNo;
        private LocalDate dateOfLoss;
        private String perilType;
        private BigDecimal amountClaimed;
        private BigDecimal amountPaid;
        private String claimStatus;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RenewalDecisionSummary {
        private Long processingId;
        private String ruleSetVersion;
        private LocalDate batchRunDate;
        private Integer computedClaimCount;
        private BigDecimal computedTotalClaimAmount;
        private BigDecimal computedLossRatio;
        private BigDecimal netLoadingDiscountPct;
        private BigDecimal finalPremiumAmount;
        private String resolutionStatus;
        private String forceActionTriggered;
        private List<CriteriaResultSummary> criteriaBreakdown;
        private UnderwriterActionSummary underwriterAction; // null if not referred
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CriteriaResultSummary {
        private String criteriaName;
        private String matchedBandLabel;
        private BigDecimal impactPctApplied;
        private String forceActionFlag;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UnderwriterActionSummary {
        private String underwriterName;
        private String actionTaken;
        private BigDecimal revisedLoadingDiscountPct;
        private String remarks;
        private LocalDateTime actionDate;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class NotificationSummary {
        private String channel;
        private Integer dayOffset;
        private LocalDateTime sentAt;
        private String deliveryStatus;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PaymentSummary {
        private BigDecimal amountPaid;
        private String paymentMode;
        private String paymentStatus;
        private LocalDateTime transactionDate;
    }
}
