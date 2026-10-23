package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "notification_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @ManyToOne @JoinColumn(name = "processing_id", nullable = false)
    private RenewalProcessing processing;

    @ManyToOne @JoinColumn(name = "policy_id", nullable = false)
    private PolicyMasterRecord policy;

    @ManyToOne @JoinColumn(name = "template_id", nullable = false)
    private NotificationTemplate template;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private NotificationTemplate.Channel channel;

    @Column(nullable = false)
    private Integer dayOffset;
    private LocalDateTime sentAt;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    @Builder.Default
    private DeliveryStatus deliveryStatus = DeliveryStatus.SENT;

    private String failureReason;

    public enum DeliveryStatus { SENT, DELIVERED, FAILED, BOUNCED }
}
