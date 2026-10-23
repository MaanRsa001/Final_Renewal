package com.insurance.renewal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** Result of a "resend payment link" request. */
@Getter @Builder @AllArgsConstructor
public class PaymentLinkResponseDTO {
    private Long transactionId;
    private String policyNo;
    private String channel;
    /** Masked destination, e.g. j***@gmail.com */
    private String sentTo;
    private String paymentLink;
    private LocalDateTime sentAt;
    private String message;
}
