package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "renewal_processing")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RenewalProcessing {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long processingId;

    @ManyToOne @JoinColumn(name = "renewal_due_id", nullable = false)
    private RenewalDue renewalDue;

    @ManyToOne @JoinColumn(name = "policy_id", nullable = false)
    private PolicyMasterRecord policy;

    @ManyToOne @JoinColumn(name = "rule_set_id", nullable = false)
    private RuleSet ruleSet;

    @Column(nullable = false)
    private LocalDate batchRunDate;

    @Builder.Default
    private Integer computedClaimCount = 0;
    @Builder.Default
    private BigDecimal computedTotalClaimAmount = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal computedLossRatio = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal netLoadingDiscountPct = BigDecimal.ZERO;
    private BigDecimal finalPremiumAmount;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ResolutionStatus resolutionStatus;

    private String forceActionTriggered;
    private LocalDateTime processedAt;

    @Column(name = "policy_no", length = 50)
    private String policyNo;
    
    public enum ResolutionStatus {
        AUTO_RENEWAL_ELIGIBLE, REFERRAL_PENDING, REJECTED, RENEWED, BUSINESS_LOST
    }
}
