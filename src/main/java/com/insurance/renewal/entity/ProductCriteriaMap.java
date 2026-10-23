package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;

/** Which criteria from the shared library this product actually evaluates, and in what order. */
@Entity @Table(name = "mst_product_criteria_map")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductCriteriaMap {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer mapId;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @ManyToOne @JoinColumn(name = "criteria_id", nullable = false)
    private Criteria criteria;

    @Builder.Default
    private Boolean isMandatory = true;
    @Builder.Default
    private Integer displaySequence = 1;
    
    @Column(name = "product_name", length = 100)
    private String productName;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "criteria_name", length = 100)
    private String criteriaName;
}
