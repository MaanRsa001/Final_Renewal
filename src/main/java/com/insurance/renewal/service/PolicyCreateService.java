package com.insurance.renewal.service;

import com.insurance.renewal.dto.PolicyCreateRequestDTO;
import com.insurance.renewal.dto.PolicyCreateResponseDTO;
import com.insurance.renewal.entity.PolicyMasterRecord;
import com.insurance.renewal.repository.PolicyMasterRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Handles policy_master INSERT. All details (product, customer, branch, agent, contact) are stored
 * directly on policy_master, so no mst_* look-ups or foreign-key validation are needed.
 * Separated from PolicyService to keep each class focused.
 */
@Service
@RequiredArgsConstructor
public class PolicyCreateService {

    private final PolicyMasterRecordRepository policyRepo;

    /**
     * Validates the request, builds a PolicyMasterRecord and persists it.
     * Returns a flat response DTO - no circular references, safe to serialize.
     *
     * @throws ResponseStatusException 409 if policyNo already exists
     * @throws ResponseStatusException 400 on an invalid date range or policyStatus
     */
    @Transactional
    public PolicyCreateResponseDTO create(PolicyCreateRequestDTO req) {

        // 1. Duplicate policyNo check
        if (policyRepo.existsByPolicyNo(req.getPolicyNo())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Policy number already exists: " + req.getPolicyNo());
        }

        // 2. Validate date range
        if (!req.getPolicyExpiryDate().isAfter(req.getPolicyStartDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "policyExpiryDate must be after policyStartDate");
        }

        // 3. Resolve policyStatus (default = ACTIVE)
        PolicyMasterRecord.PolicyStatus status = PolicyMasterRecord.PolicyStatus.ACTIVE;
        if (req.getPolicyStatus() != null && !req.getPolicyStatus().isBlank()) {
            try {
                status = PolicyMasterRecord.PolicyStatus.valueOf(req.getPolicyStatus().trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid policyStatus. Allowed: ACTIVE, EXPIRED, LAPSED");
            }
        }

        // 4. Build and persist the entity
        PolicyMasterRecord saved = policyRepo.save(
                PolicyMasterRecord.builder()
                        .policyNo(req.getPolicyNo())
                        .productId(req.getProductId())
                        .productName(req.getProductName())
                        .customerId(req.getCustomerId())
                        .customerName(req.getCustomerName())
                        .mobileNo(req.getMobileNo())
                        .email(req.getEmail())
                        .branchId(req.getBranchId())
                        .branchName(req.getBranchName())
                        .agentId(req.getAgentId())
                        .sumInsured(req.getSumInsured())
                        .premiumLc(req.getPremiumLc())
                        .policyStartDate(req.getPolicyStartDate())
                        .policyExpiryDate(req.getPolicyExpiryDate())
                        .vehicleRegNo(req.getVehicleRegNo())
                        .vehicleMfgDate(req.getVehicleMfgDate())
                        .vehicleMfgCountry(req.getVehicleMfgCountry())
                        .driverDob(req.getDriverDob())
                        .policyStatus(status.name())
                        .companyId(req.getCompanyId())
                        .sectionId(req.getSectionId())
                        .currency(req.getCurrency())
                        .premiumFc(req.getPremiumFc())
                        .sourceType(req.getSourceType())
                        .quoteNo(req.getQuoteNo())
                        .endtCount(req.getEndtCount())
                        .firstName(req.getFirstName())
                        .idProofType(req.getIdProofType())
                        .idProofNumber(req.getIdProofNumber())
                        .customerType(req.getCustomerType())
                        .applicationId(req.getApplicationId())
                        .loginId(req.getLoginId())
                        .brokerCode(req.getBrokerCode())
                        .accountExecutiveId(req.getAccountExecutiveId())
                        .makeId(req.getMakeId())
                        .makeDescription(req.getMakeDescription())
                        .modelId(req.getModelId())
                        .modelDescription(req.getModelDescription())
                        .bodytypeId(req.getBodytypeId())
                        .bodytypeDesc(req.getBodytypeDesc())
                        .vehicleusageId(req.getVehicleusageId())
                        .vehicleusageDesc(req.getVehicleusageDesc())
                        .policyTerm(req.getPolicyTerm())
                        .noClaimDiscount(req.getNoClaimDiscount())
                        .policyType(req.getPolicyType())
                        .extraAttributes(req.getExtraAttributes() == null || req.getExtraAttributes().isNull()
                                ? null : req.getExtraAttributes().toString())
                        .build()
        );

        // 5. Build flat response
        return PolicyCreateResponseDTO.builder()
                .policyId(saved.getPolicyId())
                .policyNo(saved.getPolicyNo())
                .productId(saved.getProductId())
                .productName(saved.getProductName())
                .customerId(saved.getCustomerId())
                .customerName(saved.getCustomerName())
                .customerMobile(saved.getMobileNo())
                .customerEmail(saved.getEmail())
                .branchId(saved.getBranchId())
                .branchName(saved.getBranchName())
                .agentId(saved.getAgentId())
                .agentName(saved.getAgentDisplayName())
                .sumInsured(saved.getSumInsured())
                .premiumLc(saved.getPremiumLc())
                .policyStartDate(saved.getPolicyStartDate())
                .policyExpiryDate(saved.getPolicyExpiryDate())
                .policyStatus(saved.getPolicyStatus())
                .vehicleRegNo(saved.getVehicleRegNo())
                .vehicleMfgDate(saved.getVehicleMfgDate())
                .vehicleMfgCountry(saved.getVehicleMfgCountry())
                .driverDob(saved.getDriverDob())
                .companyId(saved.getCompanyId())
                .sectionId(saved.getSectionId())
                .currency(saved.getCurrency())
                .premiumFc(saved.getPremiumFc())
                .sourceType(saved.getSourceType())
                .quoteNo(saved.getQuoteNo())
                .endtCount(saved.getEndtCount())
                .firstName(saved.getFirstName())
                .idProofType(saved.getIdProofType())
                .idProofNumber(saved.getIdProofNumber())
                .customerType(saved.getCustomerType())
                .applicationId(saved.getApplicationId())
                .loginId(saved.getLoginId())
                .brokerCode(saved.getBrokerCode())
                .accountExecutiveId(saved.getAccountExecutiveId())
                .makeId(saved.getMakeId())
                .makeDescription(saved.getMakeDescription())
                .modelId(saved.getModelId())
                .modelDescription(saved.getModelDescription())
                .bodytypeId(saved.getBodytypeId())
                .bodytypeDesc(saved.getBodytypeDesc())
                .vehicleusageId(saved.getVehicleusageId())
                .vehicleusageDesc(saved.getVehicleusageDesc())
                .policyTerm(saved.getPolicyTerm())
                .noClaimDiscount(saved.getNoClaimDiscount())
                .policyType(saved.getPolicyType())
                .extraAttributes(saved.getExtraAttributes())
                .build();
    }
}
