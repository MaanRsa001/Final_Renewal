package com.insurance.renewal.controller;

import com.insurance.renewal.dto.CustomerLookupDTO;
import com.insurance.renewal.repository.PolicyMasterRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Customers are read from policy_master (mst_customer is no longer used), so this is read-only. */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {
    private final PolicyMasterRecordRepository policyRepo;

    @GetMapping
//    @PreAuthorize("hasAnyAuthority('UNDERWRITER','BRANCH_MANAGER','AGENT')")
    public List<CustomerLookupDTO> findAll() { return policyRepo.findDistinctCustomers(); }

    @GetMapping("/search")
    public List<CustomerLookupDTO> searchByMobile(@RequestParam String mobileNo) {
        return policyRepo.findDistinctCustomersByMobile(mobileNo);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerLookupDTO> findOne(@PathVariable String id) {
        return policyRepo.findDistinctCustomers().stream()
                .filter(c -> id.equals(c.customerId()))
                .findFirst()
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
