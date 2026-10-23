package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "notification_schedule_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationScheduleConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer scheduleId;

    @Column(name = "product_id", nullable = false)
    private Integer productId;
    
    @Column(name = "product_name", nullable = false)
    private String productName;
    
    @Column(nullable = false)
    private Integer dayOffset;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private NotificationTemplate.Channel channel;

    @ManyToOne @JoinColumn(name = "template_id", nullable = false)
    private NotificationTemplate template;

    @Builder.Default
    private Boolean isActive = true;
}
