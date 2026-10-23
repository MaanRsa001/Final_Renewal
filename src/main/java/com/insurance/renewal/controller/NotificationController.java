package com.insurance.renewal.controller;

import com.insurance.renewal.entity.NotificationLog;
import com.insurance.renewal.entity.NotificationScheduleConfig;
import com.insurance.renewal.entity.NotificationTemplate;
import com.insurance.renewal.repository.NotificationLogRepository;
import com.insurance.renewal.repository.NotificationScheduleConfigRepository;
import com.insurance.renewal.repository.NotificationTemplateRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.insurance.renewal.dto.Policy360ViewDTO;
import com.insurance.renewal.entity.RenewalProcessing;
import com.insurance.renewal.repository.RenewalProcessingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationLogRepository logRepo;
    private final NotificationTemplateRepository templateRepo;
    private final NotificationScheduleConfigRepository scheduleRepo;
    private final RenewalProcessingRepository processingRepo;

    @GetMapping("/logs/policy/{policyId}")
    public List<NotificationLog> logsByPolicy(@PathVariable Long policyId) { return logRepo.findByPolicy_PolicyId(policyId); }

    @GetMapping("/templates")
    public List<NotificationTemplate> templates() { return templateRepo.findAll(); }

    @PostMapping("/templates")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public NotificationTemplate createTemplate(@Valid @RequestBody NotificationTemplate t) { return templateRepo.save(t); }

    @PutMapping("/templates/{id}")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public ResponseEntity<NotificationTemplate> updateTemplate(@PathVariable Integer id, @Valid @RequestBody NotificationTemplate t) {
        if (!templateRepo.existsById(id)) return ResponseEntity.notFound().build();
        t.setTemplateId(id);
        return ResponseEntity.ok(templateRepo.save(t));
    }

    @DeleteMapping("/templates/{id}")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Integer id) {
        if (!templateRepo.existsById(id)) return ResponseEntity.notFound().build();
        templateRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/schedule/product/{productId}")
    public List<NotificationScheduleConfig> scheduleByProduct(@PathVariable Integer productId) {
        return scheduleRepo.findByProductId(productId);
    }

    @PostMapping("/schedule")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public NotificationScheduleConfig createSchedule(@Valid @RequestBody NotificationScheduleConfig s) { return scheduleRepo.save(s); }

    @DeleteMapping("/schedule/{id}")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Integer id) {
        if (!scheduleRepo.existsById(id)) return ResponseEntity.notFound().build();
        scheduleRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Manually record a follow-up (call / SMS / email) from the Policy 360 screen.
     * Body: { "processingId": 12, "channel": "SMS|EMAIL", "deliveryStatus": "SENT|DELIVERED|FAILED|BOUNCED" (optional) }
     * dayOffset = days left to policy expiry (T-n). Returns the row in the shape the Follow-up Log table shows.
     */
    @PostMapping("/follow-up")
    @Transactional
    public Policy360ViewDTO.NotificationSummary addFollowUp(@RequestBody Map<String, Object> body) {
        if (body.get("processingId") == null || body.get("channel") == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "processingId and channel are required.");
        Long processingId = Long.valueOf(body.get("processingId").toString());
        NotificationTemplate.Channel channel;
        NotificationLog.DeliveryStatus status;
        try {
            channel = NotificationTemplate.Channel.valueOf(body.get("channel").toString().trim().toUpperCase());
            status = body.get("deliveryStatus") == null ? NotificationLog.DeliveryStatus.SENT
                    : NotificationLog.DeliveryStatus.valueOf(body.get("deliveryStatus").toString().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid channel or deliveryStatus.");
        }
        RenewalProcessing processing = processingRepo.findById(processingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Renewal processing not found: " + processingId));

        // template_id is NOT NULL: reuse any active template for the channel, else create a manual one.
        NotificationTemplate template = templateRepo.findAll().stream()
                .filter(t -> t.getChannel() == channel && !Boolean.FALSE.equals(t.getIsActive()))
                .findFirst()
                .orElseGet(() -> templateRepo.save(NotificationTemplate.builder()
                        .templateCode("MANUAL_FOLLOWUP_" + channel)
                        .channel(channel)
                        .subject("Manual follow-up")
                        .bodyTemplate("Manual follow-up recorded by user.")
                        .isActive(true).build()));

        LocalDate expiry = processing.getPolicy().getPolicyExpiryDate();
        int dayOffset = expiry == null ? 0 : (int) Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), expiry));

        NotificationLog saved = logRepo.save(NotificationLog.builder()
                .processing(processing).policy(processing.getPolicy()).template(template)
                .channel(channel).dayOffset(dayOffset).sentAt(LocalDateTime.now())
                .deliveryStatus(status).build());

        return Policy360ViewDTO.NotificationSummary.builder()
                .channel(saved.getChannel().name()).dayOffset(saved.getDayOffset())
                .sentAt(saved.getSentAt()).deliveryStatus(saved.getDeliveryStatus().name()).build();
    }
}
