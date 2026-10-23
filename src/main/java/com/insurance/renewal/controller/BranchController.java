package com.insurance.renewal.controller;

import com.insurance.renewal.dto.BranchLookupDTO;
import com.insurance.renewal.repository.PolicyMasterRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Branches are read from policy_master (mst_branch is no longer used), so this is read-only. */
@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {
    private final PolicyMasterRecordRepository policyRepo;

    @GetMapping
    public List<BranchLookupDTO> findAll() { return policyRepo.findDistinctBranches(); }

    @GetMapping("/{id}")
    public ResponseEntity<BranchLookupDTO> findOne(@PathVariable Integer id) {
        return policyRepo.findDistinctBranches().stream()
                .filter(b -> id.equals(b.branchId()))
                .findFirst()
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
