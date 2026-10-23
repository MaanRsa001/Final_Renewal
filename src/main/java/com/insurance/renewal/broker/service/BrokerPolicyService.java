package com.insurance.renewal.broker.service;

import com.insurance.renewal.broker.dto.*;
import com.insurance.renewal.broker.security.BrokerPrincipal;
import com.insurance.renewal.dto.PolicyCreateRequestDTO;
import com.insurance.renewal.dto.PolicyCreateResponseDTO;
import com.insurance.renewal.entity.PolicyMasterRecord;
import com.insurance.renewal.entity.RenewalProcessing;
import com.insurance.renewal.repository.PolicyMasterRecordRepository;
import com.insurance.renewal.repository.RenewalProcessingRepository;
import com.insurance.renewal.service.PolicyCreateService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/** Broker-facing use cases. Every method is scoped to the authenticated broker's code. */
@Service
@RequiredArgsConstructor
public class BrokerPolicyService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_RANGE_DAYS = 366;

    private final PolicyMasterRecordRepository policyRepo;
    private final RenewalProcessingRepository processingRepo;
    private final PolicyCreateService policyCreateService;

    @Transactional(readOnly = true)
    public BrokerPolicyDTO getPolicy(BrokerPrincipal broker, String policyNo) {
        return toDto(find(broker, policyNo));
    }

    @Transactional(readOnly = true)
    public BrokerRenewalQuoteDTO getRenewalQuote(BrokerPrincipal broker, String policyNo) {
        PolicyMasterRecord p = find(broker, policyNo);
        RenewalProcessing latest = latestProcessing(p)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No renewal quote has been generated yet for policy " + policyNo));
        return BrokerRenewalQuoteDTO.builder()
                .policyNo(p.getPolicyNo())
                .policyExpiryDate(p.getPolicyExpiryDate())
                .expiringPremium(p.getPremiumLc())
                .netLoadingDiscountPct(latest.getNetLoadingDiscountPct())
                .renewalPremium(latest.getFinalPremiumAmount())
                .lossRatio(latest.getComputedLossRatio())
                .renewalStatus(latest.getResolutionStatus().name())
                .decisionDate(latest.getBatchRunDate())
                .build();
    }

    @Transactional(readOnly = true)
    public BrokerPageDTO<BrokerRenewalDueDTO> renewalsDue(BrokerPrincipal broker, LocalDate from, LocalDate to,
                                                          int page, int size) {
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "toDate must not be before fromDate");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Date range must not exceed " + MAX_RANGE_DAYS + " days");
        }
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<PolicyMasterRecord> result = policyRepo.findByBrokerCodeAndPolicyStatusAndPolicyExpiryDateBetween(
                broker.brokerCode(), "ACTIVE", from, to,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by("policyExpiryDate", "policyNo")));

        LocalDate today = LocalDate.now();
        List<BrokerRenewalDueDTO> items = result.getContent().stream().map(p -> {
            Optional<RenewalProcessing> latest = latestProcessing(p);
            return BrokerRenewalDueDTO.builder()
                    .policyNo(p.getPolicyNo())
                    .customerName(p.getCustomerName())
                    .productName(p.getProductName())
                    .policyExpiryDate(p.getPolicyExpiryDate())
                    .daysToExpiry(ChronoUnit.DAYS.between(today, p.getPolicyExpiryDate()))
                    .expiringPremium(p.getPremiumLc())
                    .renewalStatus(latest.map(r -> r.getResolutionStatus().name()).orElse(null))
                    .renewalPremium(latest.map(RenewalProcessing::getFinalPremiumAmount).orElse(null))
                    .build();
        }).toList();
        return new BrokerPageDTO<>(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public BrokerPolicyDTO createPolicy(BrokerPrincipal broker, BrokerPolicyCreateRequest r) {
        PolicyCreateRequestDTO internal = PolicyCreateRequestDTO.builder()
                .policyNo(r.getPolicyNo().trim())
                .productId(r.getProductId()).productName(r.getProductName())
                .customerId(r.getCustomerId()).customerName(r.getCustomerName())
                .mobileNo(r.getCustomerMobile()).email(r.getCustomerEmail())
                .branchId(r.getBranchId()).agentId(r.getAgentId())
                .sumInsured(r.getSumInsured()).premiumLc(r.getPremium()).currency(r.getCurrency())
                .policyStartDate(r.getPolicyStartDate()).policyExpiryDate(r.getPolicyExpiryDate())
                .vehicleRegNo(r.getVehicleRegNo()).vehicleMfgDate(r.getVehicleMfgDate())
                .vehicleMfgCountry(r.getVehicleMfgCountry()).driverDob(r.getDriverDob())
                .brokerCode(broker.brokerCode())          // forced from the API key, never from the body
                .sourceType("BROKER_API")
                .policyStatus("ACTIVE")
                .build();
        PolicyCreateResponseDTO saved = policyCreateService.create(internal);
        return toDto(find(broker, saved.getPolicyNo()));
    }

    // ---- helpers ----
    private PolicyMasterRecord find(BrokerPrincipal broker, String policyNo) {
        // Same 404 whether the policy does not exist or belongs to another broker: no cross-broker probing.
        return policyRepo.findByBrokerCodeAndPolicyNo(broker.brokerCode(), policyNo.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found: " + policyNo));
    }

    private Optional<RenewalProcessing> latestProcessing(PolicyMasterRecord p) {
        return processingRepo.findByPolicy_PolicyIdOrderByBatchRunDateDesc(p.getPolicyId()).stream().findFirst();
    }

    private BrokerPolicyDTO toDto(PolicyMasterRecord p) {
        return BrokerPolicyDTO.builder()
                .policyNo(p.getPolicyNo()).policyStatus(p.getPolicyStatus())
                .productId(p.getProductId()).productName(p.getProductName())
                .customerId(p.getCustomerId()).customerName(p.getCustomerName())
                .customerMobile(p.getMobileNo()).customerEmail(p.getEmail())
                .sumInsured(p.getSumInsured()).premium(p.getPremiumLc()).currency(p.getCurrency())
                .policyStartDate(p.getPolicyStartDate()).policyExpiryDate(p.getPolicyExpiryDate())
                .vehicleRegNo(p.getVehicleRegNo()).brokerCode(p.getBrokerCode())
                .build();
    }
}
