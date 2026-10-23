package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity @Table(name = "mst_loss_ratio_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LossRatioConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer configId;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "product_name", length = 100)
    private String productName;
    
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private BasisType basisType;

    @Builder.Default
    private Integer claimLookbackYears = 3;

    @Column(nullable = false)
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    @Builder.Default
    private Boolean isActive = true;

    public enum BasisType { PREMIUM_PAID, SUM_INSURED }
}
