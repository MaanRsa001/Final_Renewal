package com.insurance.renewal.controller;

import com.insurance.renewal.dto.PaymentLinkResponseDTO;
import com.insurance.renewal.entity.PaymentTransaction;
import com.insurance.renewal.entity.PolicyMasterRecord;
import com.insurance.renewal.repository.PaymentTransactionRepository;
import com.insurance.renewal.repository.PolicyMasterRecordRepository;
import com.insurance.renewal.repository.RenewalProcessingRepository;
import com.insurance.renewal.service.PaymentLinkService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentTransactionRepository repo;
    private final RenewalProcessingRepository processingRepo;
    private final PolicyMasterRecordRepository policyMasterRecordRepository;
    private final PaymentLinkService paymentLinkService;

    @GetMapping
    public List<PaymentTransaction> findAll() { return repo.findAll(); }

    @GetMapping("/policy/{policyId}")
    public List<PaymentTransaction> findByPolicy(@PathVariable Long policyId) { return repo.findByPolicy_PolicyId(policyId); }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentTransaction> findOne(@PathVariable Long id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public PaymentTransaction create(@Valid @RequestBody PaymentTransaction p) {
        Long policyId = p.getPolicy().getPolicyId();
        PolicyMasterRecord policy = policyMasterRecordRepository
                .findById(policyId)
                .orElseThrow(() ->
                        new RuntimeException("Policy not found: " + policyId));

        p.setPolicy(policy);
        return repo.save(p);
    }

    /** Simulated payment gateway callback: on SUCCESS, auto-marks the renewal RENEWED (per requirement). */
    @PostMapping("/{id}/gateway-callback")
//    @PreAuthorize("hasAnyAuthority('CUSTOMER','AGENT','BRANCH_MANAGER')")
    @Transactional
    public ResponseEntity<PaymentTransaction> gatewayCallback(@PathVariable Long id, @RequestParam String status, @RequestParam(required = false) String gatewayReference) {
        PaymentTransaction txn = repo.findById(id).orElseThrow(() -> new NoSuchElementException("Transaction not found"));
        txn.setPaymentStatus(PaymentTransaction.PaymentStatus.valueOf(status));
        txn.setGatewayReference(gatewayReference);
        txn.setTransactionDate(LocalDateTime.now());
        repo.save(txn);

        if (txn.getPaymentStatus() == PaymentTransaction.PaymentStatus.SUCCESS) {
            var processing = txn.getProcessing();
            processing.setResolutionStatus(com.insurance.renewal.entity.RenewalProcessing.ResolutionStatus.RENEWED);
            processingRepo.save(processing);
        }
        return ResponseEntity.ok(txn);
    }

    /** Re-sends the payment link of a pending payment to the customer (channel: EMAIL or SMS, optional). */
    @PostMapping("/{id}/resend-link")
    public ResponseEntity<PaymentLinkResponseDTO> resendLink(
            @PathVariable Long id, @RequestParam(required = false) String channel) {
        return ResponseEntity.ok(paymentLinkService.resend(id, channel));
    }

    @PutMapping("/{id}")
//    @PreAuthorize("hasAnyAuthority('UNDERWRITER','BRANCH_MANAGER')")
    public ResponseEntity<PaymentTransaction> update(@PathVariable Long id, @Valid @RequestBody PaymentTransaction p) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        p.setTransactionId(id);
        return ResponseEntity.ok(repo.save(p));
    }

    @DeleteMapping("/{id}")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
