package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "notification_template")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationTemplate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer templateId;
    @Column(nullable = false, unique = true, length = 40)
    private String templateCode;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Channel channel;

    private String subject;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String bodyTemplate;
    @Builder.Default
    private Boolean isActive = true;

    public enum Channel { SMS, EMAIL }
}
