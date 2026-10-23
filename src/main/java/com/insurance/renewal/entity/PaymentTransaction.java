package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "payment_transaction")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;

    @ManyToOne @JoinColumn(name = "processing_id", nullable = false)
    private RenewalProcessing processing;

    @ManyToOne @JoinColumn(name = "policy_id", nullable = false)
    private PolicyMasterRecord policy;

    @Column(name = "policy_no", length = 30)
    private String policyNo;
    
    @Column(nullable = false)
    private BigDecimal amountPaid;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private PaymentMode paymentMode;

    private String gatewayReference;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.INITIATED;

    /** Id of the user who collected the payment (plain column - mst_user is no longer used). */
    @Column(name = "collected_by_user_id")
    private Integer collectedByUserId;

    private LocalDateTime transactionDate;

    public enum PaymentMode { ONLINE, BRANCH_COLLECTED }
    public enum PaymentStatus { INITIATED, SUCCESS, FAILED, REFUNDED }
}
