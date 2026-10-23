package com.insurance.renewal.service;

import com.insurance.renewal.dto.*;
import com.insurance.renewal.entity.*;
import com.insurance.renewal.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class RenewalDashboardService {

    private final RenewalProcessingRepository processingRepo;
    private final UnderwriterActionRepository underwriterActionRepo;
    private final PaymentTransactionRepository paymentRepo;

    @PersistenceContext
    private EntityManager em;

    // ---- Worklist grids: Due / Completed(Renewed) / Referral / Rejected / Business Lost ----

    public List<RenewalDashboardRowDTO> byStatus(RenewalProcessing.ResolutionStatus status) {
        return toRows(processingRepo.findByResolutionStatus(status));
    }

    public List<RenewalDashboardRowDTO> byBranchAndStatus(Integer branchId, RenewalProcessing.ResolutionStatus status) {
        return toRows(processingRepo.findByPolicy_BranchIdAndResolutionStatus(branchId, status));
    }

    public List<RenewalDashboardRowDTO> byBranch(Integer branchId) {
        return toRows(processingRepo.findByPolicy_BranchId(branchId));
    }

    public List<RenewalDashboardRowDTO> byAgent(Integer agentId) {
        return toRows(processingRepo.findByPolicy_AgentId(agentId));
    }

    private List<RenewalDashboardRowDTO> toRows(List<RenewalProcessing> list) {
        return list.stream().map(p -> RenewalDashboardRowDTO.builder()
                .processingId(p.getProcessingId())
                .policyId(p.getPolicy().getPolicyId())
                .policyNo(p.getPolicy().getPolicyNo())
                .productId(p.getPolicy().getProductId())
                .productName(p.getPolicy().getProductName())
                .customerName(p.getPolicy().getCustomerName())
                .branchCode(p.getPolicy().getBranchId())
                .branchName(p.getPolicy().getBranchName())
                .agentName(p.getPolicy().getAgentDisplayName())
                .policyExpiryDate(p.getPolicy().getPolicyExpiryDate())
                .netLoadingDiscountPct(p.getNetLoadingDiscountPct())
                .finalPremiumAmount(p.getFinalPremiumAmount())
                .resolutionStatus(p.getResolutionStatus().name())
                .build()
        ).toList();
    }

    // ---- Underwriter: approve / reject a referral ----

    @Transactional
    public void actionReferral(Long processingId, UnderwriterActionRequestDTO req) {
        RenewalProcessing processing = processingRepo.findById(processingId)
                .orElseThrow(() -> new NoSuchElementException("Renewal processing record not found"));
        UnderwriterAction.ActionTaken action = UnderwriterAction.ActionTaken.valueOf(req.getActionTaken());

        underwriterActionRepo.save(UnderwriterAction.builder()
                .processing(processing)
                .userId(req.getUserId())
                .actionTaken(action)
                .revisedLoadingDiscountPct(req.getRevisedLoadingDiscountPct())
                .remarks(req.getRemarks())
                .actionDate(LocalDateTime.now())
                .build());

        switch (action) {
            case APPROVED -> processing.setResolutionStatus(RenewalProcessing.ResolutionStatus.AUTO_RENEWAL_ELIGIBLE);
            case REJECTED -> processing.setResolutionStatus(RenewalProcessing.ResolutionStatus.REJECTED);
            case MODIFIED -> {
                processing.setNetLoadingDiscountPct(req.getRevisedLoadingDiscountPct());
                processing.setResolutionStatus(RenewalProcessing.ResolutionStatus.AUTO_RENEWAL_ELIGIBLE);
            }
        }
        processingRepo.save(processing);
    }

    // ---- Branch Manager: mark renewed manually (branch-collected payment) ----

    @Transactional
    public void markRenewedManually(Long processingId, ManualRenewalRequestDTO req) {
        RenewalProcessing processing = processingRepo.findById(processingId)
                .orElseThrow(() -> new NoSuchElementException("Renewal processing record not found"));
        paymentRepo.save(PaymentTransaction.builder()
                .processing(processing)
                .policy(processing.getPolicy())
                .amountPaid(req.getAmountPaid())
                .paymentMode(PaymentTransaction.PaymentMode.BRANCH_COLLECTED)
                .paymentStatus(PaymentTransaction.PaymentStatus.SUCCESS)
                .collectedByUserId(req.getCollectedByUserId())
                .transactionDate(LocalDateTime.now())
                .build());

        processing.setResolutionStatus(RenewalProcessing.ResolutionStatus.RENEWED);
        processingRepo.save(processing);
    }

    // ---- Underwriter: reject outright (no referral flow needed) ----

    @Transactional
    public void rejectRenewal(Long processingId, String remarks, Integer underwriterUserId) {
        RenewalProcessing processing = processingRepo.findById(processingId)
                .orElseThrow(() -> new NoSuchElementException("Renewal processing record not found"));
        underwriterActionRepo.save(UnderwriterAction.builder()
                .processing(processing)
                .userId(underwriterUserId)
                .actionTaken(UnderwriterAction.ActionTaken.REJECTED)
                .remarks(remarks)
                .actionDate(LocalDateTime.now())
                .build());

        processing.setResolutionStatus(RenewalProcessing.ResolutionStatus.REJECTED);
        processingRepo.save(processing);
    }
    
 // ---- Underwriter / Branch Manager: mark business lost (policy expired, customer never renewed) ----

    @Transactional
    public void markBusinessLost(Long processingId, String remarks) {
        RenewalProcessing processing = processingRepo.findById(processingId)
                .orElseThrow(() -> new NoSuchElementException("Renewal processing record not found"));
        processing.setResolutionStatus(RenewalProcessing.ResolutionStatus.BUSINESS_LOST);
        processingRepo.save(processing);
    }
    
    // ---- Agent-wise counts for a date range + product ----

    public List<AgentRenewalCountDTO> agentRenewalCounts(LocalDate startDate, LocalDate endDate,
                                                         Integer productId, Integer agentId) {
        if (startDate == null || endDate == null || productId == null) {
            throw new IllegalArgumentException("startDate, endDate and productId are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }

        Map<Integer, AgentRenewalCountDTO> byAgent = new LinkedHashMap<>();
        for (Object[] row : countByAgentAndStatus(productId, startDate, endDate, agentId)) {
            Integer agent = (Integer) row[0];
            RenewalProcessing.ResolutionStatus status = (RenewalProcessing.ResolutionStatus) row[1];
            long count = ((Number) row[2]).longValue();
            String agentName = (row[3] == null || String.valueOf(row[3]).isBlank()) ? String.valueOf(agent) : String.valueOf(row[3]);

            AgentRenewalCountDTO dto = byAgent.computeIfAbsent(agent, a -> AgentRenewalCountDTO.builder()
                    .agentId(a).agentName(agentName)
                    .renewalDueCount(0L).renewalCompleteCount(0L)
                    .renewalLostCount(0L).renewalReferralCount(0L).totalCount(0L)
                    .totalPremium(BigDecimal.ZERO)
                    .build());

            switch (status) {
                case AUTO_RENEWAL_ELIGIBLE -> dto.setRenewalDueCount(dto.getRenewalDueCount() + count);
                case RENEWED               -> dto.setRenewalCompleteCount(dto.getRenewalCompleteCount() + count);
                case BUSINESS_LOST, REJECTED -> dto.setRenewalLostCount(dto.getRenewalLostCount() + count);
                case REFERRAL_PENDING      -> dto.setRenewalReferralCount(dto.getRenewalReferralCount() + count);
            }
            dto.setTotalCount(dto.getTotalCount() + count);
            if (row[4] != null) {
                dto.setTotalPremium(dto.getTotalPremium().add((BigDecimal) row[4]));   // overall_premium_lc
            }
        }
        return List.copyOf(byAgent.values());
    }

    /** Criteria API: [agentId, resolutionStatus, count, agentName, sum(overallPremiumLc)] grouped by agent + status. */
    private List<Object[]> countByAgentAndStatus(Integer productId, LocalDate startDate, LocalDate endDate, Integer agentId) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<RenewalProcessing> root = cq.from(RenewalProcessing.class);
        Join<RenewalProcessing, PolicyMasterRecord> policy = root.join("policy");

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(policy.get("productId"), productId));
        predicates.add(cb.between(policy.<LocalDate>get("policyExpiryDate"), startDate, endDate));
        predicates.add(cb.isNotNull(policy.get("agentId")));
        if (agentId != null) {
            predicates.add(cb.equal(policy.get("agentId"), agentId));
        }

        cq.multiselect(policy.get("agentId"), root.get("resolutionStatus"), cb.count(root),
                cb.greatest(policy.<String>get("agentName")),
                cb.sum(policy.<BigDecimal>get("overallPremiumLc")))
          .where(predicates.toArray(new Predicate[0]))
          .groupBy(policy.get("agentId"), root.get("resolutionStatus"))
          .orderBy(cb.asc(policy.get("agentId")));

        return em.createQuery(cq).getResultList();
    }

}
