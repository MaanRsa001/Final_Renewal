package com.insurance.renewal.broker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Builder
@Schema(name = "RenewalDue", description = "A policy of the broker that is due for renewal.")
public class BrokerRenewalDueDTO {
    private String policyNo;
    private String customerName;
    private String productName;
    private LocalDate policyExpiryDate;
    private long daysToExpiry;
    private BigDecimal expiringPremium;
    @Schema(description = "Null until the renewal batch has processed the policy") private String renewalStatus;
    @Schema(description = "Null until the renewal batch has processed the policy") private BigDecimal renewalPremium;
}
