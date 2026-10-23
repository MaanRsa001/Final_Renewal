package com.insurance.renewal.controller;

import com.insurance.renewal.dto.*;
import com.insurance.renewal.entity.RenewalProcessing;
import com.insurance.renewal.service.RenewalDashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.List;

/**
 * Renewal worklist grids (Due / Renewed-Completed / Referral / Rejected / Business Lost)
 * plus the workflow actions each role can take on them.
 */
@RestController
@RequestMapping("/api/renewals")
@RequiredArgsConstructor
public class RenewalDashboardController {
    private final RenewalDashboardService service;

    // ---- Underwriter: branch-wise view of all buckets ----
    @GetMapping("/status/{status}")
//    @PreAuthorize("hasAnyAuthority('UNDERWRITER','BRANCH_MANAGER','AGENT')")
    public List<RenewalDashboardRowDTO> byStatus(@PathVariable RenewalProcessing.ResolutionStatus status) {
        return service.byStatus(status);
    }

    @GetMapping("/branch/{branchId}/status/{status}")
//    @PreAuthorize("hasAnyAuthority('UNDERWRITER','BRANCH_MANAGER','AGENT')")
    public List<RenewalDashboardRowDTO> byBranchAndStatus(@PathVariable Integer branchId, @PathVariable RenewalProcessing.ResolutionStatus status) {
        return service.byBranchAndStatus(branchId, status);
    }

    // ---- Branch Manager: all renewals for own branch ----
    @GetMapping("/branch/{branchId}")
//    @PreAuthorize("hasAnyAuthority('UNDERWRITER','BRANCH_MANAGER')")
    public List<RenewalDashboardRowDTO> byBranch(@PathVariable Integer branchId) { return service.byBranch(branchId); }

    // ---- Agent: my renewal bucket ----
    @GetMapping("/agent/{agentId}")
//    @PreAuthorize("hasAnyAuthority('UNDERWRITER','BRANCH_MANAGER','AGENT')")
    public List<RenewalDashboardRowDTO> byAgent(@PathVariable Integer agentId) { return service.byAgent(agentId); }

    // ---- Underwriter: approve / reject / modify a referral ----
    @PostMapping("/{processingId}/underwriter-action")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public void actionReferral(@PathVariable Long processingId, @Valid @RequestBody UnderwriterActionRequestDTO req) {
        service.actionReferral(processingId, req);
    }

    // ---- Underwriter: outright reject ----
    @PostMapping("/{processingId}/reject")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public void reject(@PathVariable Long processingId, @RequestParam Integer underwriterUserId, @RequestParam(required = false) String remarks) {
        service.rejectRenewal(processingId, remarks, underwriterUserId);
    }

    // ---- Branch Manager: mark renewed on branch-collected payment ----
    @PostMapping("/{processingId}/mark-renewed")
//    @PreAuthorize("hasAuthority('BRANCH_MANAGER')")
    public void markRenewed(@PathVariable Long processingId, @Valid @RequestBody ManualRenewalRequestDTO req) {
        service.markRenewedManually(processingId, req);
    }
    
    // ---- Underwriter / Branch Manager: mark business lost (expired, customer did not renew) ----
    @PostMapping("/{processingId}/mark-lost") 
//    @PreAuthorize("hasAnyAuthority('UNDERWRITER','BRANCH_MANAGER')")
    public void markLost(@PathVariable Long processingId, @RequestParam(required = false) String remarks) {
        service.markBusinessLost(processingId, remarks);
    }
    
    // ---- Agent-wise counts (due / complete / lost / referral / total) for date range + product ----
    // GET /api/renewals/agent-counts?startDate=2026-01-01&endDate=2026-12-31&productId=1[&agentId=101]
    @GetMapping("/agent-counts")
    public List<AgentRenewalCountDTO> agentCounts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam Integer productId,
            @RequestParam(required = false) Integer agentId) {
        return service.agentRenewalCounts(startDate, endDate, productId, agentId);
    }
}
