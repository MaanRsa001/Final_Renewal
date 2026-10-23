package com.insurance.renewal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * One row returned by GET /api/home-position-master/upcoming-expiry and inserted into policy_master.
 * Field names follow the policy_master columns.
 *
 * <pre>
 * home_position_master     : policyNo, originalPolicyNo, productId, customerId, branchId(BRANCH_CODE),
 *                            agentId(AGENCY_CODE), policyStartDate(INCEPTION_DATE), policyExpiryDate,
 *                            policyStatus(STATUS), companyId, productName, customerName, branchName,
 *                            sectionId, currency, premiumLc, premiumFc, sourceType, quoteNo, endtCount,
 *                            applicationId, loginId, brokerCode, accountExecutiveId(AC_EXECUTIVE_ID),
 *                            policyTerm, noClaimDiscount
 * motor_data_details       : sumInsured, vehicleRegNo, vehicleMfgDate, makeId, makeDescription, modelId,
 *                            modelDescription, bodytypeId, bodytypeDesc, vehicleusageId, vehicleusageDesc, policyType
 * eway_motor_makemodel_master : vehicleMfgCountry (VEH_MANF_COUNTRY), extraAttributes (all other columns)
 * motor_driver_details     : driverDob
 * personal_info            : mobileNo, email, firstName, idProofType, idProofNumber, customerType
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomePositionMasterDTO {

    // ---- home_position_master ----
    private String policyNo;

    /**
     * home_position_master.ORIGINAL_POLICY_NO.
     * When non-blank this becomes the POLICY_NO written to policy_master;
     * policyNo (the current/latest amended number) is kept here for reference only.
     */
    private String originalPolicyNo;

    private Integer productId;
    private String customerId;
    private String branchId;
    private String agentId;
    /** login_user_info.USER_NAME for home_position_master.LOGIN_ID; stored in policy_master.agent_name. */
    private String agentName;
    private LocalDate policyStartDate;
    private LocalDate policyExpiryDate;
    private String policyStatus;
    private Integer companyId;
    private String productName;
    private String customerName;
    private String branchName;
    private Integer sectionId;
    private String currency;
    private BigDecimal premiumLc;
    private BigDecimal premiumFc;
    private BigDecimal overallPremiumFc;
    private BigDecimal overallPremiumLc;
    private String sourceType;
    private String quoteNo;
    private Integer endtCount;
    private String applicationId;
    private String loginId;
    private String brokerCode;
    private String accountExecutiveId;
    private String policyTerm;
    private BigDecimal noClaimDiscount;

    // ---- premium-calc API (EwayCommonApi POST /common/premium-calc) ----
    /** Renewal premium including tax (foreign currency) = sum of the selected covers' PremiumIncludedTax. */
    @JsonProperty("PremiumIncludedTax")
    private BigDecimal premiumIncludedTax;
    /** Renewal premium including tax (local currency) = sum of the selected covers' PremiumIncludedTaxLC. */
    @JsonProperty("PremiumIncludedTaxLC")
    private BigDecimal premiumIncludedTaxLC;
    /** "OK" when premium-calc succeeded, otherwise the reason PremiumIncludedTax is null. */
    @JsonProperty("PremiumCalcMessage")
    private String premiumCalcMessage;

    // ---- motor_data_details ----
    private BigDecimal sumInsured;
    private String vehicleRegNo;
    private LocalDate vehicleMfgDate;
    private String makeId;
    private String makeDescription;
    private String modelId;
    private String modelDescription;
    private String bodytypeId;
    private String bodytypeDesc;
    private String vehicleusageId;
    private String vehicleusageDesc;
    private String policyType;

    // ---- motor_data_details (additional columns) ----
    private String requestReferenceNo;
    private Integer locationId;
    private String vehicleId;
    private String windScreenCoverRequeired;
    private String insuranceType;
    private String chassisNumber;
    private String fuelType;
    private Integer seatingCapacity;
    private String periodOfInsurance;
    private BigDecimal windScreenSumInsured;
    private BigDecimal accessoriesSumInsured;
    private Integer ncdYears;
    private String ncdYn;
    private Long vdRefNo;
    private LocalDateTime entryDate;
    private BigDecimal exchangeRate;
    private String fleetOwnerYn;
    private Integer noOfVehicles;
    private String havePromoCode;
    private BigDecimal windScreenSumInsuredLc;
    private BigDecimal accessoriesSumInsuredLc;
    private BigDecimal sumInsuredLc;
    private BigDecimal cubicCapacity;
    private String drivenBy;
    private String carAlarmYn;
    private Integer paymentFrequency;
    private String previousInsuranceYn;
    private Integer zone;

    // ---- eway_motor_makemodel_master ----
    private String vehicleMfgCountry;
    /** Every column of the matching make-model row except VEH_MANF_COUNTRY. */
    private Map<String, Object> extraAttributes;

    // ---- motor_driver_details ----
    private LocalDate driverDob;

    // ---- personal_info ----
    private String mobileNo;
    private String email;
    private String firstName;
    private String idProofType;
    private String idProofNumber;
    private String customerType;
}
