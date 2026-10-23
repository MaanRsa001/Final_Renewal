package com.insurance.renewal.service;

import com.insurance.renewal.dto.PaymentLinkResponseDTO;
import com.insurance.renewal.entity.NotificationLog;
import com.insurance.renewal.entity.NotificationTemplate;
import com.insurance.renewal.entity.PaymentTransaction;
import com.insurance.renewal.entity.PolicyMasterRecord;
import com.insurance.renewal.repository.NotificationLogRepository;
import com.insurance.renewal.repository.NotificationTemplateRepository;
import com.insurance.renewal.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

/**
 * Re-sends the payment link of a pending (INITIATED / FAILED) payment transaction to the customer.
 * EMAIL is sent through Spring Mail (configure spring.mail.*). SMS needs a provider - see sendSms().
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentLinkService {
    private final PaymentTransactionRepository paymentRepo;
    private final NotificationTemplateRepository templateRepo;
    private final NotificationLogRepository logRepo;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /** Base URL of the payment page; the transaction id is appended. */
    @Value("${app.payment.link-base-url:http://192.168.1.18:4600/#/pay/}")
    private String linkBaseUrl;

    @Value("${app.payment.mail-from:}")
    private String mailFrom;

    @Transactional
    public PaymentLinkResponseDTO resend(Long transactionId, String requestedChannel) {
        PaymentTransaction txn = paymentRepo.findById(transactionId)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + transactionId));

        if (txn.getPaymentStatus() == PaymentTransaction.PaymentStatus.SUCCESS
                || txn.getPaymentStatus() == PaymentTransaction.PaymentStatus.REFUNDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment is already " + txn.getPaymentStatus() + "; a payment link cannot be resent.");
        }

        PolicyMasterRecord policy = txn.getPolicy();
        NotificationTemplate.Channel channel = resolveChannel(requestedChannel, policy);
        String link = linkBaseUrl + txn.getTransactionId();
        String policyNo = txn.getPolicyNo() != null ? txn.getPolicyNo() : String.valueOf(policy.getPolicyId());
        String name = policy.getCustomerName() != null ? policy.getCustomerName() : "Customer";
        String body = "Dear " + name + ",\n\nPlease use the link below to pay the renewal premium of "
                + txn.getAmountPaid() + " for policy " + policyNo + ":\n" + link
                + "\n\nIf you have already paid, please ignore this message.";

        String destination;
        try {
            if (channel == NotificationTemplate.Channel.EMAIL) {
                destination = policy.getEmail();
                sendEmail(destination, "Renewal payment link - Policy " + policyNo, body);
            } else {
                destination = policy.getMobileNo();
                sendSms(destination, body);
            }
        } catch (ResponseStatusException e) {
            writeLog(txn, channel, NotificationLog.DeliveryStatus.FAILED, e.getReason());
            throw e;
        } catch (Exception e) {
            log.error("Failed to send payment link for transaction {}", transactionId, e);
            writeLog(txn, channel, NotificationLog.DeliveryStatus.FAILED, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not send the payment link: " + e.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();
        writeLog(txn, channel, NotificationLog.DeliveryStatus.SENT, null);
        return PaymentLinkResponseDTO.builder()
                .transactionId(txn.getTransactionId())
                .policyNo(policyNo)
                .channel(channel.name())
                .sentTo(mask(destination))
                .paymentLink(link)
                .sentAt(now)
                .message("Payment link sent via " + channel + " to " + mask(destination))
                .build();
    }

    /** EMAIL / SMS as requested; when not given, EMAIL if the customer has an email, otherwise SMS. */
    private NotificationTemplate.Channel resolveChannel(String requested, PolicyMasterRecord policy) {
        if (requested != null && !requested.isBlank()) {
            try {
                return NotificationTemplate.Channel.valueOf(requested.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channel must be EMAIL or SMS.");
            }
        }
        return isBlank(policy.getEmail()) ? NotificationTemplate.Channel.SMS : NotificationTemplate.Channel.EMAIL;
    }

    private void sendEmail(String to, String subject, String body) {
        if (isBlank(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer has no email address on the policy.");
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Email is not configured. Set spring.mail.host / username / password in application.properties.");
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        if (!isBlank(mailFrom)) msg.setFrom(mailFrom);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        sender.send(msg);
    }

    /** No SMS provider is configured in this project yet - plug your gateway client in here. */
    private void sendSms(String mobile, String body) {
        if (isBlank(mobile)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer has no mobile number on the policy.");
        }
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                "SMS gateway is not configured. Use channel=EMAIL or add an SMS provider in PaymentLinkService.sendSms().");
    }

    /** Audit trail in notification_log. Skipped when no template exists for the channel (template_id is NOT NULL). */
    private void writeLog(PaymentTransaction txn, NotificationTemplate.Channel channel,
                          NotificationLog.DeliveryStatus status, String failure) {
        NotificationTemplate template = templateRepo.findAll().stream()
                .filter(t -> t.getChannel() == channel && !Boolean.FALSE.equals(t.getIsActive()))
                .filter(t -> t.getTemplateCode() != null && t.getTemplateCode().toUpperCase().contains("PAYMENT"))
                .findFirst()
                .orElseGet(() -> templateRepo.findAll().stream()
                        .filter(t -> t.getChannel() == channel && !Boolean.FALSE.equals(t.getIsActive()))
                        .findFirst().orElse(null));
        if (template == null || txn.getProcessing() == null) {
            log.warn("No {} notification template found - payment link send not written to notification_log", channel);
            return;
        }
        logRepo.save(NotificationLog.builder()
                .processing(txn.getProcessing())
                .policy(txn.getPolicy())
                .template(template)
                .channel(channel)
                .dayOffset(0)
                .sentAt(LocalDateTime.now())
                .deliveryStatus(status)
                .failureReason(failure != null && failure.length() > 250 ? failure.substring(0, 250) : failure)
                .build());
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String mask(String v) {
        if (v == null || v.length() < 4) return v;
        int at = v.indexOf('@');
        if (at > 1) return v.charAt(0) + "***" + v.substring(at);
        return "***" + v.substring(v.length() - 4);
    }
}
