package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * READ-ONLY mapping of ewayglobal.personal_info.
 * Real PK = CUSTOMER_ID + COMPANY_ID + CUSTOMER_REFERENCE_NO.
 */
@Entity
@Table(name = "personal_info")
@IdClass(PersonalInfo.Pk.class)
@Getter @Setter @NoArgsConstructor
public class PersonalInfo {

    @Id @Column(name = "CUSTOMER_ID")
    private String customerId;

    @Id @Column(name = "COMPANY_ID")
    private String companyId;

    @Id @Column(name = "CUSTOMER_REFERENCE_NO")
    private String customerReferenceNo;

    @Column(name = "MOBILE_NO_1")
    private String mobileNo1;

    @Column(name = "MOBILE_NO_2")
    private String mobileNo2;

    @Column(name = "MOBILE_NO_3")
    private String mobileNo3;

    @Column(name = "EMAIL_1")
    private String email1;

    @Column(name = "FIRST_NAME")
    private String firstName;

    @Column(name = "CLIENT_NAME")
    private String clientName;

    /** Raw numeric/code value from ID_TYPE lookup, e.g. "1", "2". */
    @Column(name = "ID_TYPE")
    private String idType;

    /** Human-readable description e.g. "AADHAAR", "PAN". */
    @Column(name = "ID_TYPE_DESC")
    private String idTypeDesc;

    @Column(name = "ID_NUMBER")
    private String idNumber;

    /** DOB for individuals, registration date for companies. */
    @Column(name = "DOB_OR_REG_DATE")
    private LocalDate dobOrRegDate;

    @Column(name = "ENTRY_DATE")
    private LocalDateTime entryDate;

    @Column(name = "POLICY_HOLDER_TYPE")
    private String policyHolderType;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Pk implements Serializable {
        private String customerId;
        private String companyId;
        private String customerReferenceNo;
    }
}
