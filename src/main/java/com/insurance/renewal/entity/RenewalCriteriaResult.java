package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "renewal_criteria_result")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RenewalCriteriaResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long resultId;

    @ManyToOne @JoinColumn(name = "processing_id", nullable = false)
    private RenewalProcessing processing;

    @ManyToOne @JoinColumn(name = "criteria_id", nullable = false)
    private Criteria criteria;

    @Column(name = "criteria_name", length = 100)
    private String criteriaName;
    
    private String matchedBandLabel;
    @Column(nullable = false)
    private BigDecimal impactPctApplied;
    private String forceActionFlag;
}
