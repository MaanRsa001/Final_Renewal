package com.insurance.renewal.controller;

import com.insurance.renewal.service.RenewalRecalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;

/** Backend of the "Recalculate" button on the Renewal Decision card. */
@RestController
@RequestMapping("/api/renewal-recalculate")
@RequiredArgsConstructor
public class RenewalRecalculateController {

    private final RenewalRecalculationService recalculationService;

    /** POST /api/renewal-recalculate?policyNo=P11/2025/101/99999/99999/4000003   (or ?policyId=362) */
    @PostMapping
    public ResponseEntity<Map<String, Object>> recalculate(@RequestParam(required = false) String policyNo,
                                                            @RequestParam(required = false) Long policyId) {
        if ((policyNo == null || policyNo.isBlank()) && policyId == null)
            return ResponseEntity.badRequest().body(Map.of("error", "policyNo or policyId is required"));
        try {
            return ResponseEntity.ok(recalculationService.recalculate(policyId, policyNo));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
        }
    }
}
