package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "mst_criteria_band")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CriteriaBand {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer bandId;

    @ManyToOne @JoinColumn(name = "criteria_id", nullable = false)
    private Criteria criteria;

    @Column(name = "criteria_name", length = 100)
    private String criteriaName;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "product_id")
    private Integer productId;

    @Column(name = "product_name", length = 100)
    private String productName;

    @Column(nullable = false, length = 50)
    private String bandLabel;
    private BigDecimal rangeFrom;
    private BigDecimal rangeTo;
    private String enumValue;
    @Builder.Default
    private Boolean isActive = true;
}
