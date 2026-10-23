package com.insurance.renewal.broker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Builder
@Schema(name = "RenewalQuote", description = "Latest renewal decision for a policy.")
public class BrokerRenewalQuoteDTO {
    private String policyNo;
    private LocalDate policyExpiryDate;
    @Schema(description = "Premium of the expiring policy") private BigDecimal expiringPremium;
    @Schema(description = "Net loading (+) / discount (-) in percent") private BigDecimal netLoadingDiscountPct;
    @Schema(description = "Renewal premium after loading/discount") private BigDecimal renewalPremium;
    @Schema(description = "Claims loss ratio used by the rules") private BigDecimal lossRatio;
    @Schema(description = "Renewal workflow status, e.g. AUTO_RENEWAL_ELIGIBLE, REFERRAL_PENDING, REJECTED, RENEWED, BUSINESS_LOST")
    private String renewalStatus;
    private LocalDate decisionDate;
}
