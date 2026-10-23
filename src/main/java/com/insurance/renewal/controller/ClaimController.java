package com.insurance.renewal.controller;

import com.insurance.renewal.entity.Claim;
import com.insurance.renewal.repository.ClaimRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
public class ClaimController {
    private final ClaimRepository repo;

    @GetMapping
    public List<Claim> findAll() { return repo.findAll(); }

    @GetMapping("/policy/{policyId}")
    public List<Claim> findByPolicy(@PathVariable Long policyId) { return repo.findByPolicy_PolicyId(policyId); }

    @GetMapping("/{id}")
    public ResponseEntity<Claim> findOne(@PathVariable Long id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public Claim create(@Valid @RequestBody Claim c) { return repo.save(c); }

    @PutMapping("/{id}")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public ResponseEntity<Claim> update(@PathVariable Long id, @Valid @RequestBody Claim c) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        c.setClaimId(id);
        return ResponseEntity.ok(repo.save(c));
    }

    @DeleteMapping("/{id}")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
