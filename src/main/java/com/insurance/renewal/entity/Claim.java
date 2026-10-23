package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name = "claims")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Claim {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long claimId;

    @ManyToOne @JoinColumn(name = "policy_id", nullable = false)
    private PolicyMasterRecord policy;
    @Column(nullable = false, unique = true, length = 30)
    private String policyNo;
    @Column(nullable = false, unique = true, length = 30)
    private String claimNo;
    @Column(nullable = false)
    private LocalDate dateOfLoss;
    @Column(nullable = false)
    private LocalDate dateReported;
    private String perilType;
    @Column(nullable = false)
    private BigDecimal amountClaimed;
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    @Builder.Default
    private ClaimStatus claimStatus = ClaimStatus.OPEN;

    private LocalDate closedDate;

    public enum ClaimStatus { OPEN, CLOSED, REPUDIATED }
}
