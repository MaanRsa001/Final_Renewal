package com.insurance.renewal.controller;

import com.insurance.renewal.dto.AgentLookupDTO;
import com.insurance.renewal.repository.PolicyMasterRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Agents are read from policy_master (mst_agent is no longer used), so this is read-only. */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {
    private final PolicyMasterRecordRepository policyRepo;

    @GetMapping
    public List<AgentLookupDTO> findAll() { return policyRepo.findDistinctAgents(); }

    @GetMapping("/branch/{branchId}")
    public List<AgentLookupDTO> findByBranch(@PathVariable Integer branchId) {
        return policyRepo.findDistinctAgentsByBranch(branchId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgentLookupDTO> findOne(@PathVariable Integer id) {
        return policyRepo.findDistinctAgents().stream()
                .filter(a -> id.equals(a.agentId()))
                .findFirst()
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
