package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "underwriter_action")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UnderwriterAction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long actionId;

    @ManyToOne @JoinColumn(name = "processing_id", nullable = false)
    private RenewalProcessing processing;

//    @ManyToOne @JoinColumn(name = "user_id", nullable = false)
//    private AppUser user;
    /** Id of the underwriter who took the action (plain column - mst_user is no longer used). */
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ActionTaken actionTaken;

    private BigDecimal revisedLoadingDiscountPct;
    private String remarks;
    private LocalDateTime actionDate;

    public enum ActionTaken { APPROVED, REJECTED, MODIFIED }
}
