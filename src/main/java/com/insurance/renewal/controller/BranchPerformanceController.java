package com.insurance.renewal.controller;

import com.insurance.renewal.dto.BranchAgentPerformanceDTO;
import com.insurance.renewal.entity.RenewalProcessing;
import com.insurance.renewal.repository.PolicyMasterRecordRepository;
import com.insurance.renewal.repository.RenewalProcessingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Branch Manager dashboard: agent-wise pending/completed/business-lost counts. */
@RestController
@RequestMapping("/api/branch-performance")
@RequiredArgsConstructor
public class BranchPerformanceController {
    private final PolicyMasterRecordRepository policyRepo;
    private final RenewalProcessingRepository processingRepo;

    @GetMapping("/branch/{branchId}")
//    @PreAuthorize("hasAnyAuthority('UNDERWRITER','BRANCH_MANAGER')")
    public List<BranchAgentPerformanceDTO> agentPerformance(@PathVariable Integer branchId) {
        return policyRepo.findDistinctAgentsByBranch(branchId).stream().map(agent -> {
            List<RenewalProcessing> all = processingRepo.findByPolicy_AgentId(agent.agentId());
            long pending = all.stream().filter(p -> p.getResolutionStatus() == RenewalProcessing.ResolutionStatus.AUTO_RENEWAL_ELIGIBLE
                    || p.getResolutionStatus() == RenewalProcessing.ResolutionStatus.REFERRAL_PENDING).count();
            long completed = all.stream().filter(p -> p.getResolutionStatus() == RenewalProcessing.ResolutionStatus.RENEWED).count();
            long lost = all.stream().filter(p -> p.getResolutionStatus() == RenewalProcessing.ResolutionStatus.BUSINESS_LOST
                    || p.getResolutionStatus() == RenewalProcessing.ResolutionStatus.REJECTED).count();
            return BranchAgentPerformanceDTO.builder()
                    .agentId(agent.agentId())
                    .agentName(agent.agentName())
                    .pendingCount(pending)
                    .completedCount(completed)
                    .businessLostCount(lost)
                    .build();
        }).toList();
    }
}
