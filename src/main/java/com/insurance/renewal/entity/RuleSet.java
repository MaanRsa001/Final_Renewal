package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "rule_set")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RuleSet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer ruleSetId;

//    @ManyToOne @JoinColumn(name = "product_id", nullable = false)
//    private Product product;
    
    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "product_name", length = 100)
    private String productName;

    @Column(nullable = false, length = 40)
    private String versionCode;
    @Column(nullable = false)
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private BigDecimal maxLoadingCapPct;
    private BigDecimal maxDiscountCapPct;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    @Builder.Default
    private Status status = Status.DRAFT;

//    @ManyToOne @JoinColumn(name = "created_by_user_id")
//    private AppUser createdBy;
    @Column(name = "created_by_user_id")
    private Integer createdByUserId;
    
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;

    public enum Status { DRAFT, ACTIVE, EXPIRED }
}
