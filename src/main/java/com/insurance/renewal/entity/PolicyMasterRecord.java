package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Flat (no foreign-key relations) mapping of policy_master - the ONLY policy entity in this project.
 *
 * Every detail the renewal module needs (product, customer, branch, agent, contact, vehicle, premium)
 * is stored on policy_master itself, so nothing here depends on mst_product / mst_customer / mst_branch /
 * mst_agent (or on the old policy_master_pre_json_backup table).
 *
 * Used by the home_position_master -> policy_master sync, the renewal batch, the dashboards and the
 * Policy 360 view.
 */
@Entity
@Table(name = "policy_master")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PolicyMasterRecord {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long policyId;

    private String policyNo;
    @Column(name = "product_id")           private Integer productId;        // home_position_master.PRODUCT_ID
    @Column(name = "customer_id")          private String customerId;        // home_position_master.CUSTOMER_ID
    @Column(name = "branch_id")            private Integer branchId;          // home_position_master.BRANCH_CODE
    @Column(name = "agent_id")             private Integer agentId;           // home_position_master.AGENCY_CODE
    @Column(name = "agent_name")           private String agentName;          // login_user_info.USER_NAME (by home_position_master.LOGIN_ID)
    private BigDecimal sumInsured;    // motor_data_details.SUM_INSURED
    private LocalDate policyStartDate; // INCEPTION_DATE
    private LocalDate policyExpiryDate; // EXPIRY_DATE
    private String vehicleRegNo;      // motor_data_details.REGISTRATION_NUMBER
    private LocalDate vehicleMfgDate; // motor_data_details.MANUFACTURE_YEAR
    private String vehicleMfgCountry; // eway_motor_makemodel_master.VEH_MANF_COUNTRY
    private LocalDate driverDob;      // motor_driver_details.DRIVER_DOB
    private String policyStatus;      // home_position_master.STATUS ('P' is stored as ACTIVE - column is enum ACTIVE/EXPIRED/LAPSED)

    /** JSON: every eway_motor_makemodel_master column except VEH_MANF_COUNTRY. */
    @Column(name = "extraattributes", columnDefinition = "json")
    private String extraAttributes;

    @Column(name = "company_id")           private Integer companyId;
    @Column(name = "product_name")         private String productName;
    @Column(name = "customer_name")        private String customerName;
    @Column(name = "branch_name")          private String branchName;
    @Column(name = "section_id")           private Integer sectionId;
    @Column(name = "currency")             private String currency;
    @Column(name = "premiumlc")            private BigDecimal premiumLc;
    @Column(name = "premiumfc")            private BigDecimal premiumFc;
    @Column(name = "overall_premium_fc")   private BigDecimal overallPremiumFc;   // home_position_master.OVERALL_PREMIUM_FC
    @Column(name = "overall_premium_lc")   private BigDecimal overallPremiumLc;   // home_position_master.OVERALL_PREMIUM_LC
    /** Overall premium after the renewal net loading (+%) / discount (-%) - filled by the renewal batch / recalculate. */
    @Column(name = "final_premium_lc")     private BigDecimal finalPremiumLc;
    @Column(name = "final_premium_fc")     private BigDecimal finalPremiumFc;
    @Column(name = "source_type")          private String sourceType;
    @Column(name = "quote_no")             private String quoteNo;
    @Column(name = "endt_count")           private Integer endtCount;
    @Column(name = "mobile_no")            private String mobileNo;          // personal_info.MOBILE_NO_1
    @Column(name = "email")                private String email;             // personal_info.EMAIL_1
    @Column(name = "first_name")           private String firstName;         // personal_info.FIRST_NAME
    @Column(name = "id_proof_type")        private String idProofType;       // personal_info.ID_TYPE
    @Column(name = "id_proof_number")      private String idProofNumber;     // personal_info.ID_NUMBER
    @Column(name = "application_id")       private String applicationId;
    @Column(name = "login_id")             private String loginId;
    @Column(name = "broker_code")          private String brokerCode;
    @Column(name = "account_executive_id") private String accountExecutiveId;
    @Column(name = "make_id")              private Integer makeId;
    @Column(name = "make_description")     private String makeDescription;
    @Column(name = "model_id")             private Integer modelId;
    @Column(name = "model_description")    private String modelDescription;
    @Column(name = "bodytype_id")          private Integer bodytypeId;        // motor_data_details.VEHICLE_TYPE
    @Column(name = "bodytype_desc")        private String bodytypeDesc;      // motor_data_details.VEHICLE_TYPE_DESC
    @Column(name = "vehicleusage_id")      private Integer vehicleusageId;    // motor_data_details.MOTOR_USAGE
    @Column(name = "vehicleusage_desc")    private String vehicleusageDesc;  // motor_data_details.MOTOR_USAGE_DESC
    @Column(name = "customer_type")        private String customerType;      // personal_info.POLICY_HOLDER_TYPE
    @Column(name = "policy_term")          private String policyTerm;
    @Column(name = "no_claim_discount")    private BigDecimal noClaimDiscount;
    @Column(name = "policy_type")          private String policyType;        // motor_data_details.POLICY_TYPE
    @Column(name = "usage_location_zone", length = 50)
    private String usageLocationZone;

    // ---- additional motor_data_details columns (see sql/policy_master_add_motor_columns.sql) ----
    @Column(name = "request_reference_no") private String requestReferenceNo;
    @Column(name = "location_id") private Integer locationId;
    @Column(name = "vehicle_id") private String vehicleId;
    @Column(name = "wind_screen_cover_required") private String windScreenCoverRequeired;
    @Column(name = "insurance_type") private String insuranceType;
    @Column(name = "chassis_number") private String chassisNumber;
    @Column(name = "fuel_type") private String fuelType;
    @Column(name = "seating_capacity") private Integer seatingCapacity;
    @Column(name = "period_of_insurance") private String periodOfInsurance;
    @Column(name = "wind_screen_sum_insured") private BigDecimal windScreenSumInsured;
    @Column(name = "acccessories_sum_insured") private BigDecimal accessoriesSumInsured;
    @Column(name = "ncd_years") private Integer ncdYears;
    @Column(name = "ncd_yn") private String ncdYn;
    @Column(name = "vd_refno") private Long vdRefNo;
    @Column(name = "entry_date") private LocalDateTime entryDate;
    @Column(name = "exchange_rate") private BigDecimal exchangeRate;
    @Column(name = "fleet_owner_yn") private String fleetOwnerYn;
    @Column(name = "no_of_vehicles") private Integer noOfVehicles;
    @Column(name = "havepromocode") private String havePromoCode;
    @Column(name = "wind_screen_sum_insured_lc") private BigDecimal windScreenSumInsuredLc;
    @Column(name = "acccessories_sum_insured_lc") private BigDecimal accessoriesSumInsuredLc;
    @Column(name = "sum_insured_lc") private BigDecimal sumInsuredLc;
    @Column(name = "cubic_capacity") private BigDecimal cubicCapacity;
    @Column(name = "driven_by") private String drivenBy;
    @Column(name = "car_alarm_yn") private String carAlarmYn;
    @Column(name = "payment_frequency") private Integer paymentFrequency;
    @Column(name = "previous_insurance_yn") private String previousInsuranceYn;
    @Column(name = "zone") private Integer zone;
    /** Agent name to display: policy_master.agent_name, falling back to the agent id when no name is stored. */
    public String getAgentDisplayName() {
        if (agentName != null && !agentName.isBlank()) return agentName;
        return agentId == null ? null : String.valueOf(agentId);
    }

    /** Allowed values of policy_status (column is enum ACTIVE/EXPIRED/LAPSED). */
    public enum PolicyStatus { ACTIVE, EXPIRED, LAPSED }

    /**
     * The premium of the expiring policy, used as the base for the renewal premium and loss ratio.
     * policy_master stores it as premiumlc, so this is a read-only alias (not a column) that keeps the
     * "previousPremium" source field of existing criteria rules and API consumers working.
     */
    public BigDecimal getPreviousPremium() {
        return overallPremiumLc != null ? overallPremiumLc : premiumLc;
    }
    }
