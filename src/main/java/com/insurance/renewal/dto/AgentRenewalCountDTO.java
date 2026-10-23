package com.insurance.renewal.dto;

import lombok.*;

import java.math.BigDecimal;

/** Agent-wise renewal counts for a date range + product (due / completed / lost / referral / total). */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentRenewalCountDTO {
    private Integer agentId;
    private String agentName;
    private Long renewalDueCount;       // AUTO_RENEWAL_ELIGIBLE
    private Long renewalCompleteCount;  // RENEWED
    private Long renewalLostCount;      // BUSINESS_LOST + REJECTED
    private Long renewalReferralCount;  // REFERRAL_PENDING
    private Long totalCount;
    private BigDecimal totalPremium;    // SUM(policy_master.overall_premium_lc)
}
