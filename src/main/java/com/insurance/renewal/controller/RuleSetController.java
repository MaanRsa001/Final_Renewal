package com.insurance.renewal.controller;

import com.insurance.renewal.entity.Criteria;
import com.insurance.renewal.entity.RuleDefinition;
import com.insurance.renewal.entity.RuleSet;
import com.insurance.renewal.repository.CriteriaRepository;
import com.insurance.renewal.repository.RuleDefinitionRepository;
import com.insurance.renewal.repository.RuleSetRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Master Setup: month-wise/effective-dated Rule Sets and their Rule Definitions. Underwriter-only write access. */
@RestController
@RequestMapping("/api/rule-sets")
@RequiredArgsConstructor
public class RuleSetController {
    private final RuleSetRepository ruleSetRepo;
    private final RuleDefinitionRepository ruleDefRepo;
    private final CriteriaRepository criteriaRepo;

    @GetMapping
    public List<RuleSet> findAll() { return ruleSetRepo.findAll(); }

    @GetMapping("/product/{productId}")
    public List<RuleSet> findByProduct(@PathVariable Integer productId) { return ruleSetRepo.findByProductId(productId); }

    @GetMapping("/{id}")
    public ResponseEntity<RuleSet> findOne(@PathVariable Integer id) {
        return ruleSetRepo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public RuleSet create(@Valid @RequestBody RuleSet rs) { return ruleSetRepo.save(rs); }

    @PutMapping("/{id}")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public ResponseEntity<RuleSet> update(@PathVariable Integer id, @Valid @RequestBody RuleSet rs) {
        if (!ruleSetRepo.existsById(id)) return ResponseEntity.notFound().build();
        rs.setRuleSetId(id);
        return ResponseEntity.ok(ruleSetRepo.save(rs));
    }

    @DeleteMapping("/{id}")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        if (!ruleSetRepo.existsById(id)) return ResponseEntity.notFound().build();
        ruleSetRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- rule definitions (child resource) ----

    @GetMapping("/{ruleSetId}/rules")
    public List<RuleDefinition> findRules(@PathVariable Integer ruleSetId) {
        return ruleDefRepo.findByRuleSet_RuleSetId(ruleSetId);
    }

    @PostMapping("/{ruleSetId}/rules")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public RuleDefinition createRule(@PathVariable Integer ruleSetId, @Valid @RequestBody RuleDefinition rule) {
        RuleSet rs = ruleSetRepo.findById(ruleSetId).orElseThrow();
        rule.setRuleSet(rs);
        resolveCriteria(rule);
        return ruleDefRepo.save(rule);
    }

    @PutMapping("/rules/{ruleDefId}")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public ResponseEntity<RuleDefinition> updateRule(@PathVariable Integer ruleDefId, @Valid @RequestBody RuleDefinition rule) {
        return ruleDefRepo.findById(ruleDefId).map(existing -> {
            rule.setRuleDefId(ruleDefId);
            rule.setRuleSet(existing.getRuleSet());   // keep the parent rule set
            resolveCriteria(rule);
            return ResponseEntity.ok(ruleDefRepo.save(rule));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/rules/{ruleDefId}")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public ResponseEntity<Void> deleteRule(@PathVariable Integer ruleDefId) {
        if (!ruleDefRepo.existsById(ruleDefId)) return ResponseEntity.notFound().build();
        ruleDefRepo.deleteById(ruleDefId);
        return ResponseEntity.noContent().build();
    }

    private void resolveCriteria(RuleDefinition rule) {
        if (rule.getCriteria() == null || rule.getCriteria().getCriteriaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "criteria.criteriaId is required");
        }
        Criteria c = criteriaRepo.findById(rule.getCriteria().getCriteriaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Criteria not found: " + rule.getCriteria().getCriteriaId()));
        rule.setCriteria(c);
        if (rule.getCriteriaName() == null || rule.getCriteriaName().isBlank()) {
            rule.setCriteriaName(c.getCriteriaName());
        }
    }
}
