package com.insurance.renewal.controller;

import com.insurance.renewal.service.RenewalBatchOrchestrator;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/** Underwriter's "Batch renewal processing" screen action - also runs automatically every morning (see RenewalBatchOrchestrator). */
@RestController
@RequestMapping("/api/renewal-batch")
@RequiredArgsConstructor
public class BatchController {
    private final RenewalBatchOrchestrator batchOrchestrator;

    @PostMapping("/run") 
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public Map<String, Object> runNow(@RequestParam(required = false) String runDate) {
        LocalDate date = runDate != null ? LocalDate.parse(runDate) : LocalDate.now();
        return batchOrchestrator.run(date);
    }
}
