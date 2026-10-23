package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity @Table(name = "renewal_due")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RenewalDue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long renewalDueId;

    @ManyToOne @JoinColumn(name = "policy_id", nullable = false)
    private PolicyMasterRecord policy;

    @Column(nullable = false)
    private LocalDate expiryDate;
    @Column(nullable = false)
    private Integer daysToExpiry;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    @Builder.Default
    private Status currentStatus = Status.DUE;

    private LocalDate batchFirstPickedDate;
    private LocalDate batchLastProcessedDate;

    @Column(name = "policy_no", length = 50)
    private String policyNo;
    
    public enum Status { DUE, IN_PROGRESS, CLOSED }
}
