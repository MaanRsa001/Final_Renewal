package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * READ-ONLY mapping of ewayglobal.home_position_master.
 * Only the columns needed by the renewal sync are mapped (ddl-auto = none, so nothing is created/altered).
 */
@Entity
@Table(name = "home_position_master")
@Getter @Setter @NoArgsConstructor
public class HomePositionMaster {

    @Id
    @Column(name = "HOME_SNO")
    private Long homeSno;

    @Column(name = "QUOTE_NO")
    private String quoteNo;

    @Column(name = "COMPANY_ID")
    private String companyId;

    @Column(name = "PRODUCT_ID")
    private Integer productId;

    @Column(name = "PRODUCT_NAME")
    private String productName;

    @Column(name = "SECTION_ID")
    private Integer sectionId;

    @Column(name = "AGENCY_CODE")
    private Integer agencyCode;

    @Column(name = "CUSTOMER_ID")
    private String customerId;

    @Column(name = "BRANCH_CODE")
    private String branchCode;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "POLICY_NO")
    private String policyNo;

    @Column(name = "ORIGINAL_POLICY_NO")
    private String originalPolicyNo;

    @Column(name = "ENDT_COUNT")
    private Integer endtCount;

    @Column(name = "INCEPTION_DATE")
    private LocalDateTime inceptionDate;

    @Column(name = "EXPIRY_DATE")
    private LocalDateTime expiryDate;

    @Column(name = "CURRENCY")
    private String currency;

    @Column(name = "PREMIUM_LC")
    private Double premiumLc;

    @Column(name = "SOURCE_TYPE")
    private String sourceType;

    @Column(name = "CUSTOMER_NAME")
    private String customerName;

    @Column(name = "BRANCH_NAME")
    private String branchName;

    @Column(name = "PREMIUM_FC")
    private Double premiumFc;

    @Column(name = "OVERALL_PREMIUM_FC")
    private Double overallPremiumFc;

    @Column(name = "OVERALL_PREMIUM_LC")
    private Double overallPremiumLc;

    @Column(name = "APPLICATION_ID")
    private String applicationId;

    @Column(name = "LOGIN_ID")
    private String loginId;

    @Column(name = "BROKER_CODE")
    private String brokerCode;

    @Column(name = "AC_EXECUTIVE_ID")
    private String acExecutiveId;

    @Column(name = "POLICY_TERM")
    private String policyTerm;

    @Column(name = "NO_CLAIM_DISCOUNT")
    private Double noClaimDiscount;
}
