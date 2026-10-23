package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * READ-ONLY mapping of ewayglobal.motor_data_details.
 * Real PK = QUOTE_NO + REQUEST_REFERENCE_NO + LOCATION_ID + VEHICLE_ID.
 */
@Entity
@Table(name = "motor_data_details")
@IdClass(MotorDataDetails.Pk.class)
@Getter @Setter @NoArgsConstructor
public class MotorDataDetails {

    @Id @Column(name = "QUOTE_NO")
    private String quoteNo;

    @Id @Column(name = "REQUEST_REFERENCE_NO")
    private String requestReferenceNo;

    @Id @Column(name = "LOCATION_ID")
    private Integer locationId;

    @Id @Column(name = "VEHICLE_ID")
    private String vehicleId;

    @Column(name = "POLICY_NO")
    private String policyNo;

    @Column(name = "COMPANY_ID")
    private String companyId;

    @Column(name = "SUM_INSURED")
    private Double sumInsured;

    @Column(name = "REGISTRATION_NUMBER")
    private String registrationNumber;

    @Column(name = "MANUFACTURE_YEAR")
    private Integer manufactureYear;

    @Column(name = "VEHICLE_MAKE_ID")
    private String vehicleMakeId;

    @Column(name = "VEHICLE_MODEL_ID")
    private String vehicleModelId;


    @Column(name = "VEHICLE_MAKE_DESC")
    private String vehicleMakeDesc;

    @Column(name = "VEHCILE_MODEL_DESC")   // (sic) column name as it exists in the table
    private String vehicleModelDesc;

    @Column(name = "VEHICLE_TYPE")
    private String vehicleType;

    @Column(name = "VEHICLE_TYPE_DESC")
    private String vehicleTypeDesc;

    @Column(name = "MOTOR_USAGE")
    private String motorUsage;

    @Column(name = "MOTOR_USAGE_DESC")
    private String motorUsageDesc;

    @Column(name = "POLICY_TYPE")
    private String policyType;


    // ---- additional columns ----
    @Column(name = "WIND_SCREEN_COVER_REQUIRED")
    private String windScreenCoverRequeired;

    @Column(name = "INSURANCE_TYPE")
    private String insuranceType;

    @Column(name = "CHASSIS_NUMBER")
    private String chassisNumber;

    @Column(name = "FUEL_TYPE")
    private String fuelType;

    @Column(name = "SEATING_CAPACITY")
    private Integer seatingCapacity;

    @Column(name = "PERIOD_OF_INSURANCE")
    private String periodOfInsurance;

    @Column(name = "WIND_SCREEN_SUM_INSURED")
    private Double windScreenSumInsured;

    @Column(name = "ACCCESSORIES_SUM_INSURED")
    private Double accessoriesSumInsured;

    @Column(name = "NCD_YEARS")
    private Integer ncdYears;

    @Column(name = "NCD_YN")
    private String ncdYn;

    @Column(name = "VD_REFNO")
    private Long vdRefNo;

    @Column(name = "ENTRY_DATE")
    private LocalDateTime entryDate;

    @Column(name = "EXCHANGE_RATE")
    private Double exchangeRate;

    @Column(name = "FLEET_OWNER_YN")
    private String fleetOwnerYn;

    @Column(name = "NO_OF_VEHICLES")
    private Integer noOfVehicles;

    @Column(name = "HAVEPROMOCODE")
    private String havePromoCode;

    @Column(name = "WIND_SCREEN_SUM_INSURED_LC")
    private Double windScreenSumInsuredLc;

    @Column(name = "ACCCESSORIES_SUM_INSURED_LC")
    private Double accessoriesSumInsuredLc;

    @Column(name = "SUM_INSURED_LC")
    private Double sumInsuredLc;

    @Column(name = "CUBIC_CAPACITY")
    private Double cubicCapacity;

    @Column(name = "DRIVEN_BY")
    private String drivenBy;

    @Column(name = "CAR_ALARM_YN")
    private String carAlarmYn;

    @Column(name = "PAYMENT_FREQUENCY")
    private Integer paymentFrequency;

//    @Column(name = "CLAIM_TYPE")
//    private String claimType;

//    @Column(name = "CUST_RENEWAL_YN")
//    private String custRenewalYn;

    @Column(name = "PREVIOUS_INSURANCE_YN")
    private String previousInsuranceYn;

    @Column(name = "ZONE")
    private Integer zone;

//    @Column(name = "CREDIT_SHORTFALL_SI")
//    private Double creditShortfallSi;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Pk implements Serializable {
        private String quoteNo;
        private String requestReferenceNo;
        private Integer locationId;
        private String vehicleId;
    }
}
