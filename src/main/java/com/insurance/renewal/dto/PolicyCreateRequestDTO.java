package com.insurance.renewal.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Request body for POST /api/policies/create
 * Everything is stored flat on policy_master: the ids (productId, customerId, branchId, agentId) plus
 * the optional display values (productName, customerName, branchName, mobileNo, email).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyCreateRequestDTO {

    @NotBlank(message = "policyNo is required")
    @Size(max = 50, message = "policyNo must be <= 50 characters")
    private String policyNo;

    @NotNull(message = "productId is required")
    private Integer productId;

    @NotNull(message = "customerId is required")
    private String customerId;

    @NotNull(message = "branchId is required")
    private Integer branchId;

    @NotNull(message = "agentId is required")
    private Integer agentId;

    @NotNull(message = "sumInsured is required")
    @DecimalMin(value = "0.01", message = "sumInsured must be > 0")
    private BigDecimal sumInsured;

    @NotNull(message = "premiumLc is required")
    @DecimalMin(value = "0.01", message = "premiumLc must be > 0")
    private BigDecimal premiumLc;

    @NotNull(message = "policyStartDate is required")
    private LocalDate policyStartDate;

    @NotNull(message = "policyExpiryDate is required")
    private LocalDate policyExpiryDate;

    // Optional display values stored on policy_master (no mst_* look-ups any more)
    private String productName;
    private String customerName;
    private String branchName;
    private String mobileNo;
    private String email;

    // Motor-specific optional fields
    private String vehicleRegNo;
    private LocalDate vehicleMfgDate;
    private String vehicleMfgCountry;
    private LocalDate driverDob;

    // Defaults to ACTIVE if omitted
    private String policyStatus;

    // ---- Remaining policy_master columns (all optional) ----
    private Integer companyId;
    private Integer sectionId;
    private String currency;
    private BigDecimal premiumFc;
    private String sourceType;
    private String quoteNo;
    private Integer endtCount;

    private String firstName;
    private String idProofType;
    private String idProofNumber;
    private String customerType;

    private String applicationId;
    private String loginId;
    private String brokerCode;
    private String accountExecutiveId;

    // Motor vehicle details
    private Integer makeId;
    private String makeDescription;
    private Integer modelId;
    private String modelDescription;
    private Integer bodytypeId;
    private String bodytypeDesc;
    private Integer vehicleusageId;
    private String vehicleusageDesc;

    private String policyTerm;
    private BigDecimal noClaimDiscount;
    private String policyType;

    /** Free-form JSON object stored in the extraattributes JSON column. */
    private JsonNode extraAttributes;
}
