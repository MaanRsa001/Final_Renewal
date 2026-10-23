package com.insurance.renewal.controller;

import com.insurance.renewal.dto.Policy360ViewDTO;
import com.insurance.renewal.dto.PolicyCreateRequestDTO;
import com.insurance.renewal.dto.PolicyCreateResponseDTO;
import com.insurance.renewal.entity.PolicyMasterRecord;
import com.insurance.renewal.repository.PolicyMasterRecordRepository;
import com.insurance.renewal.service.PolicyCreateService;
import com.insurance.renewal.service.PolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {
    private final PolicyMasterRecordRepository repo;
    private final PolicyService policyService;
    private final PolicyCreateService policyCreateService;

    @GetMapping
//    @PreAuthorize("hasAnyAuthority('UNDERWRITER','BRANCH_MANAGER','AGENT')")
    public List<PolicyMasterRecord> findAll() { return repo.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<PolicyMasterRecord> findOne(@PathVariable Long id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Policy numbers contain slashes (e.g. P11/2025/101/99999/99999/4000001), so a plain
     * {policyNo} segment never matches. {*policyNo} captures the rest of the path instead.
     *   GET /api/policies/by-policy-no/P11/2025/101/99999/99999/4000001
     */
    @GetMapping("/by-policy-no/{*policyNo}")
    public ResponseEntity<PolicyMasterRecord> findByPolicyNo(@PathVariable String policyNo) {
        return repo.findByPolicyNo(stripLeadingSlash(policyNo)).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /** Query-param variant: GET /api/policies/by-policy-no?policyNo=P11/2025/... */
    @GetMapping("/by-policy-no")
    public ResponseEntity<PolicyMasterRecord> findByPolicyNoParam(@RequestParam String policyNo) {
        return repo.findByPolicyNo(policyNo.trim()).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /** Customer self-service search: by mobile number. */
    @GetMapping("/search/by-mobile")
    public List<Policy360ViewDTO> searchByMobile(@RequestParam String mobileNo) {
        return repo.findByMobileNo(mobileNo).stream()
                .map(p -> policyService.get360View(p.getPolicyNo()))
                .toList();
    }

    @GetMapping("/branch/{branchId}")
    public List<PolicyMasterRecord> findByBranch(@PathVariable Integer branchId) { return repo.findByBranchId(branchId); }

    @GetMapping("/agent/{agentId}")
    public List<PolicyMasterRecord> findByAgent(@PathVariable Integer agentId) { return repo.findByAgentId(agentId); }

    /** THE Policy 360 View - single-screen aggregate: policy + claims + rule breakdown + notifications + payments. */
    /*
     * Policy numbers contain slashes, so the old "/{policyNo}/360-view" could never match.
     * The catch-all {*policyNo} must be the last path element, hence the order below.
     *   GET /api/policies/360-view/P11/2025/101/99999/99999/4000001
     */
    @GetMapping("/360-view/{*policyNo}")
    public Policy360ViewDTO get360View(@PathVariable String policyNo) {
        return policyService.get360View(stripLeadingSlash(policyNo));
    }

    /** Query-param variant: GET /api/policies/360-view?policyNo=P11/2025/101/99999/99999/4000001 */
    @GetMapping("/360-view")
    public Policy360ViewDTO get360ViewByParam(@RequestParam String policyNo) {
        return policyService.get360View(policyNo.trim());
    }

    /**
     * Backward-compatible OLD URL shape, policy number first:
     *   GET /api/policies/P11/2025/101/99999/99999/4000001/360-view
     * Spring 6 path patterns cannot put a "match everything" variable before a fixed
     * suffix, so we list 2..10 segment shapes and read the policy number from the raw URI.
     */
    @GetMapping({
            "/{s1}/{s2}/360-view",
            "/{s1}/{s2}/{s3}/360-view",
            "/{s1}/{s2}/{s3}/{s4}/360-view",
            "/{s1}/{s2}/{s3}/{s4}/{s5}/360-view",
            "/{s1}/{s2}/{s3}/{s4}/{s5}/{s6}/360-view",
            "/{s1}/{s2}/{s3}/{s4}/{s5}/{s6}/{s7}/360-view",
            "/{s1}/{s2}/{s3}/{s4}/{s5}/{s6}/{s7}/{s8}/360-view",
            "/{s1}/{s2}/{s3}/{s4}/{s5}/{s6}/{s7}/{s8}/{s9}/360-view",
            "/{s1}/{s2}/{s3}/{s4}/{s5}/{s6}/{s7}/{s8}/{s9}/{s10}/360-view"
    })
    public Policy360ViewDTO get360ViewLegacy(jakarta.servlet.http.HttpServletRequest request) {
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        String prefix = "/api/policies/";
        String suffix = "/360-view";
        int start = uri.indexOf(prefix) + prefix.length();
        int end = uri.lastIndexOf(suffix);
        String policyNo = java.net.URLDecoder.decode(uri.substring(start, end), java.nio.charset.StandardCharsets.UTF_8);
        return policyService.get360View(policyNo);
    }

    /** {*var} captures include the leading "/" - drop it. */
    private static String stripLeadingSlash(String v) {
        return v != null && v.startsWith("/") ? v.substring(1) : v;
    }

    @PostMapping("/create")
//    @PreAuthorize("hasAnyAuthority('UNDERWRITER','BRANCH_MANAGER','AGENT')")
    public PolicyCreateResponseDTO createPolicy(
            @Valid @RequestBody PolicyCreateRequestDTO request) {
        return policyCreateService.create(request);
    }

    @PostMapping
//    @PreAuthorize("hasAnyAuthority('UNDERWRITER','BRANCH_MANAGER','AGENT')")
    public PolicyMasterRecord create(@Valid @RequestBody PolicyMasterRecord p) {
        return repo.save(p);
    }
    
    @PutMapping("/{id}")
//    @PreAuthorize("hasAnyAuthority('UNDERWRITER','BRANCH_MANAGER','AGENT')")
    public ResponseEntity<PolicyMasterRecord> update(@PathVariable Long id, @Valid @RequestBody PolicyMasterRecord p) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        p.setPolicyId(id);
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
