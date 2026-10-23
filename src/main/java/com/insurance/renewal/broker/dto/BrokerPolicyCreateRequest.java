package com.insurance.renewal.broker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Broker policy submission. brokerCode is NOT accepted: it is always taken from the API key. */
@Data
@Schema(name = "PolicyCreateRequest")
public class BrokerPolicyCreateRequest {

    @NotBlank @Size(max = 50)
    @Schema(example = "P11/2025/101/99999/99999/4000001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String policyNo;

    @NotNull @Schema(example = "1001", requiredMode = Schema.RequiredMode.REQUIRED) private Integer productId;
    @Schema(example = "Motor Private Car") private String productName;

    @NotBlank @Schema(example = "CUST-778", requiredMode = Schema.RequiredMode.REQUIRED) private String customerId;
    @Schema(example = "Asha Mwangi") private String customerName;
    @Schema(example = "255712345678") private String customerMobile;
    @Email @Schema(example = "asha@example.com") private String customerEmail;

    @NotNull private Integer branchId;
    @NotNull private Integer agentId;

    @NotNull @DecimalMin("0.01") @Schema(example = "250000.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal sumInsured;
    @NotNull @DecimalMin("0.01") @Schema(example = "12500.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal premium;
    @Schema(example = "TZS") private String currency;

    @NotNull @Schema(example = "2025-10-01", requiredMode = Schema.RequiredMode.REQUIRED) private LocalDate policyStartDate;
    @NotNull @Schema(example = "2026-09-30", requiredMode = Schema.RequiredMode.REQUIRED) private LocalDate policyExpiryDate;

    @Schema(example = "T123ABC") private String vehicleRegNo;
    private LocalDate vehicleMfgDate;
    private String vehicleMfgCountry;
    private LocalDate driverDob;
}
