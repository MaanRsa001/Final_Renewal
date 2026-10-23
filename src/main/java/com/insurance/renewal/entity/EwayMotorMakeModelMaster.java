package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * READ-ONLY mapping of ewayglobal.eway_motor_makemodel_master.
 * Real PK = MAKE_ID, MODEL_ID, BODY_ID, COMPANY_ID, BRANCH_CODE, AMEND_ID,
 *           EFFECTIVE_DATE_END, EFFECTIVE_DATE_START.
 */
@Entity
@Table(name = "eway_motor_makemodel_master")
@IdClass(EwayMotorMakeModelMaster.Pk.class)
@Getter @Setter @NoArgsConstructor
public class EwayMotorMakeModelMaster {

    @Id @Column(name = "MAKE_ID")
    private Integer makeId;

    @Id @Column(name = "MODEL_ID")
    private Integer modelId;

    @Id @Column(name = "BODY_ID")
    private Integer bodyId;

    @Id @Column(name = "COMPANY_ID")
    private String companyId;

    @Id @Column(name = "BRANCH_CODE")
    private String branchCode;

    @Id @Column(name = "AMEND_ID")
    private Integer amendId;

    @Id @Column(name = "EFFECTIVE_DATE_END")
    private LocalDateTime effectiveDateEnd;

    @Id @Column(name = "EFFECTIVE_DATE_START")
    private LocalDateTime effectiveDateStart;

    @Column(name = "VEH_MANF_COUNTRY")
    private String vehManfCountry;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Pk implements Serializable {
        private Integer makeId;
        private Integer modelId;
        private Integer bodyId;
        private String companyId;
        private String branchCode;
        private Integer amendId;
        private LocalDateTime effectiveDateEnd;
        private LocalDateTime effectiveDateStart;
    }
}
