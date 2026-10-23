package com.insurance.renewal.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Row shape for the renewal worklist grids (Due / Renewed / Referral / Rejected / Lost). */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RenewalDashboardRowDTO {
    private Long processingId;
    private Long policyId;
    private String policyNo;
    private Integer productId;
    private String productName;
    private String customerName;
    private Integer branchCode;
    private String branchName;
    private String agentName;
    private LocalDate policyExpiryDate;
    private Integer daysToExpiry;
    private BigDecimal netLoadingDiscountPct;
    private BigDecimal finalPremiumAmount;
    private String resolutionStatus;
}
