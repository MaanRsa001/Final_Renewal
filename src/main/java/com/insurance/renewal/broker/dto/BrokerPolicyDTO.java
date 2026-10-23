package com.insurance.renewal.broker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Builder
@Schema(name = "Policy", description = "A policy placed by the calling broker.")
public class BrokerPolicyDTO {
    @Schema(example = "P11/2025/101/99999/99999/4000001") private String policyNo;
    @Schema(example = "ACTIVE", allowableValues = {"ACTIVE", "EXPIRED", "LAPSED"}) private String policyStatus;
    private Integer productId;
    private String productName;
    private String customerId;
    private String customerName;
    private String customerMobile;
    private String customerEmail;
    @Schema(example = "250000.00") private BigDecimal sumInsured;
    @Schema(description = "Premium in local currency", example = "12500.00") private BigDecimal premium;
    private String currency;
    @Schema(example = "2025-10-01") private LocalDate policyStartDate;
    @Schema(example = "2026-09-30") private LocalDate policyExpiryDate;
    private String vehicleRegNo;
    @Schema(description = "Broker code that owns the policy", example = "BRK001") private String brokerCode;
}
