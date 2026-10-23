package com.insurance.renewal.controller;

import com.insurance.renewal.entity.Criteria;
import com.insurance.renewal.entity.CriteriaBand;
import com.insurance.renewal.repository.CriteriaBandRepository;
import com.insurance.renewal.repository.CriteriaRepository;
import com.insurance.renewal.repository.PolicyMasterRecordRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Master Setup: Criteria library + their value bands - the building blocks of the rule engine. */
@RestController
@RequestMapping("/api/criteria")
@RequiredArgsConstructor
public class CriteriaController {
    private final CriteriaRepository criteriaRepo;
    private final CriteriaBandRepository bandRepo;
    private final PolicyMasterRecordRepository policyRepo;   // product name lookup for bands

    @GetMapping
    public List<Criteria> findAll() { return criteriaRepo.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<Criteria> findOne(@PathVariable Integer id) {
        return criteriaRepo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public Criteria create(@Valid @RequestBody Criteria c) { return criteriaRepo.save(c); }

    @PutMapping("/{id}")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public ResponseEntity<Criteria> update(@PathVariable Integer id, @Valid @RequestBody Criteria c) {
        if (!criteriaRepo.existsById(id)) return ResponseEntity.notFound().build();
        c.setCriteriaId(id);
        return ResponseEntity.ok(criteriaRepo.save(c));
    }

    @DeleteMapping("/{id}")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        if (!criteriaRepo.existsById(id)) return ResponseEntity.notFound().build();
        criteriaRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- bands (child resource) ----

    @GetMapping("/{criteriaId}/bands")
    public List<CriteriaBand> findBands(@PathVariable Integer criteriaId,
                                        @RequestParam(required = false) Integer companyId,
                                        @RequestParam(required = false) Integer productId) {
        // optional filters: ?companyId=100002&productId=5
        return bandRepo.findByCriteria_CriteriaId(criteriaId).stream()
                .filter(b -> companyId == null || companyId.equals(b.getCompanyId()))
                .filter(b -> productId == null || productId.equals(b.getProductId()))
                .toList();
    }

    @PostMapping("/{criteriaId}/bands")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public CriteriaBand createBand(@PathVariable Integer criteriaId, @Valid @RequestBody CriteriaBand band,
                                   @RequestParam(required = false) Integer productId) {
        Criteria c = criteriaRepo.findById(criteriaId).orElseThrow();
        band.setCriteria(c);
        fillProductDefaults(band, productId);
        return bandRepo.save(band);
    }

    @PutMapping("/bands/{bandId}")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public ResponseEntity<CriteriaBand> updateBand(@PathVariable Integer bandId, @Valid @RequestBody CriteriaBand band,
                                                   @RequestParam(required = false) Integer productId) {
        CriteriaBand existing = bandRepo.findById(bandId).orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();
        band.setBandId(bandId);
        if (band.getCriteria() == null) band.setCriteria(existing.getCriteria());   // keep parent criterion
        fillProductDefaults(band, productId);
        return ResponseEntity.ok(bandRepo.save(band));
    }

    /**
     * Fills what the screen did not send:
     *  - product_id   : the Master Setup product dropdown (body productId, else ?productId=)
     *  - product_name : looked up from policy_master by product id
     *  - company_id   : from policy_master - the company with most policies of this product
     */
    private void fillProductDefaults(CriteriaBand band, Integer productIdParam) {
        if (band.getProductId() == null) band.setProductId(productIdParam);
        if (band.getProductId() == null)   // screen sent no product: use the product with most policies in policy_master
            policyRepo.findProductIdsByPolicyCount().stream().findFirst().ifPresent(band::setProductId);
        Integer pid = band.getProductId();
        if (pid == null) return;
        if (band.getProductName() == null || band.getProductName().isBlank()) {
            policyRepo.findDistinctProducts().stream()
                    .filter(p -> pid.equals(p.productId()))
                    .map(com.insurance.renewal.dto.ProductLookupDTO::productName)
                    .filter(n -> n != null && !n.isBlank()).map(String::trim).findFirst()
                    .ifPresent(band::setProductName);
        }
        if (band.getCompanyId() == null) {
            policyRepo.findCompanyIdsByProduct(pid).stream().findFirst().ifPresent(band::setCompanyId);
        }
    }

    @DeleteMapping("/bands/{bandId}")
//    @PreAuthorize("hasAuthority('UNDERWRITER')")
    public ResponseEntity<Void> deleteBand(@PathVariable Integer bandId) {
        if (!bandRepo.existsById(bandId)) return ResponseEntity.notFound().build();
        bandRepo.deleteById(bandId);
        return ResponseEntity.noContent().build();
    }



/**
 * Curated field catalog for the "Add a criterion" screen's source-field
 * dropdown - each entry is the exact JPA property name
 * RenewalCriteriaValueResolver reflects at batch time, paired with the
 * data type it will resolve to (so the UI can steer the admin toward a
 * matching criterion data_type / value_transform combination). This is
 * a deliberately curated whitelist, not full entity introspection -
 * internal-only fields (IDs, foreign keys) are excluded on purpose.
 */
@GetMapping("/source-fields")
public Map<String, List<SourceFieldOption>> sourceFields() {
    return Map.of(
        "POLICY_MASTER", List.of(
            new SourceFieldOption("sumInsured", "Sum Insured", "NUMERIC"),
            new SourceFieldOption("previousPremium", "Previous Premium", "NUMERIC"),
            new SourceFieldOption("policyStartDate", "Policy Start Date", "DATE"),
            new SourceFieldOption("policyExpiryDate", "Policy Expiry Date", "DATE"),
            new SourceFieldOption("vehicleMfgDate", "Vehicle Manufacture Date", "DATE"),
            new SourceFieldOption("driverDob", "Driver Date of Birth", "DATE"),
            new SourceFieldOption("vehicleMfgCountry", "Vehicle Manufacture Country", "ENUM"),
            new SourceFieldOption("usageLocationZone", "Usage Location Zone", "ENUM"),
            new SourceFieldOption("policyStatus", "Policy Status", "ENUM"),
            
            new SourceFieldOption("registration_number", "Registration Number", "STRING"),
            new SourceFieldOption("vehicle_make_desc", "Vehicle Make", "STRING")
        ),
        "CLAIMS", List.of(
            new SourceFieldOption("amountClaimed", "Amount Claimed", "NUMERIC"),
            new SourceFieldOption("amountPaid", "Amount Paid", "NUMERIC"),
            new SourceFieldOption("dateOfLoss", "Date of Loss", "DATE"),
            new SourceFieldOption("dateReported", "Date Reported", "DATE"),
            new SourceFieldOption("perilType", "Peril Type", "ENUM"),
            new SourceFieldOption("claimStatus", "Claim Status", "ENUM")
        )
    );
}

public record SourceFieldOption(String field, String label, String kind) {}

@GetMapping("/aggregations")
public List<AggregationOption> aggregations() {
    return List.of(
        new AggregationOption(Criteria.Aggregation.COUNT.name(), "COUNT — number of claims"),
        new AggregationOption(Criteria.Aggregation.SUM.name(),   "SUM — total of field values"),
        new AggregationOption(Criteria.Aggregation.AVG.name(),   "AVG — average of field values"),
        new AggregationOption(Criteria.Aggregation.MAX.name(),   "MAX — highest field value"),
        new AggregationOption(Criteria.Aggregation.MIN.name(),   "MIN — lowest field value")
    );
}

/** One aggregation function as returned by GET /api/criteria/aggregations. */
public record AggregationOption(String value, String label) {}
}
