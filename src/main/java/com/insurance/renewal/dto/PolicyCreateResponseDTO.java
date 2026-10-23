package com.insurance.renewal.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response body returned after a successful policy insert.
 * Contains all persisted fields so the caller can confirm what was saved.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyCreateResponseDTO {

    private Long policyId;
    private String policyNo;

    private Integer productId;
    private String productName;

    private String customerId;
    private String customerName;
    private String customerMobile;
    private String customerEmail;

    private Integer branchId;
    private String branchName;

    private Integer agentId;
    private String agentName;

    private BigDecimal sumInsured;
    private BigDecimal premiumLc;

    private LocalDate policyStartDate;
    private LocalDate policyExpiryDate;
    private String policyStatus;

    // Motor fields
    private String vehicleRegNo;
    private LocalDate vehicleMfgDate;
    private String vehicleMfgCountry;
    private LocalDate driverDob;

    // Remaining policy_master columns
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
    private String extraAttributes;
}
