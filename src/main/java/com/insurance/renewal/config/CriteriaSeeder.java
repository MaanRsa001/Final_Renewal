package com.insurance.renewal.config;

import com.insurance.renewal.dto.ProductLookupDTO;
import com.insurance.renewal.entity.Criteria;
import com.insurance.renewal.entity.Criteria.Aggregation;
import com.insurance.renewal.entity.Criteria.DataType;
import com.insurance.renewal.entity.Criteria.SourceEntity;
import com.insurance.renewal.entity.Criteria.ValueTransform;
import com.insurance.renewal.entity.ProductCriteriaMap;
import com.insurance.renewal.repository.CriteriaRepository;
import com.insurance.renewal.repository.PolicyMasterRecordRepository;
import com.insurance.renewal.repository.ProductCriteriaMapRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fills the Master Setup "Criterion" dropdown on startup.
 *
 * 1. Inserts the default criteria into mst_criteria (only codes that don't exist yet,
 *    so it is safe to run on every restart and never overwrites admin edits).
 * 2. Maps every criterion to every product found in policy_master
 *    (mst_product_criteria_map) when that product has no mapping yet.
 *
 * Every source field used here is one RenewalCriteriaValueResolver can read
 * (PolicyMasterRecord / Claim getters, or the LOSS_RATIO computed value).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CriteriaSeeder implements ApplicationRunner {

    private final CriteriaRepository criteriaRepo;
    private final ProductCriteriaMapRepository mapRepo;
    private final PolicyMasterRecordRepository policyRepo;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Set<String> newCodes = seedCriteria();
            seedProductMappings(newCodes);
        } catch (Exception e) {
            // Never block application startup because of seed data.
            log.warn("Criteria seeding skipped: {}", e.getMessage(), e);
        }
    }

    /** @return codes of the criteria inserted in this run. */
    private Set<String> seedCriteria() {
        // Only seed an EMPTY library. Once admins have their own criteria (possibly under
        // different codes, e.g. MFG_COUNTRY / LOCATION_ZONE), inserting defaults would
        // create duplicates of the same field in the dropdown.
        if (criteriaRepo.count() > 0) return Set.of();
        Set<String> existing = Set.of();

        List<Criteria> defaults = List.of(
            // ---- Policy fields ----
            criterion("DRIVER_AGE", "Driver Age (Years)", DataType.NUMERIC_RANGE, "MOTOR",
                    SourceEntity.POLICY_MASTER, "driverDob", Aggregation.NONE, ValueTransform.AGE_IN_YEARS, null),
            criterion("VEHICLE_AGE", "Vehicle Age (Years)", DataType.NUMERIC_RANGE, "MOTOR",
                    SourceEntity.POLICY_MASTER, "vehicleMfgDate", Aggregation.NONE, ValueTransform.AGE_IN_YEARS, null),
            criterion("SUM_INSURED", "Sum Insured", DataType.NUMERIC_RANGE, "ALL",
                    SourceEntity.POLICY_MASTER, "sumInsured", Aggregation.NONE, ValueTransform.DIRECT, null),
            criterion("PREVIOUS_PREMIUM", "Previous Premium", DataType.NUMERIC_RANGE, "ALL",
                    SourceEntity.POLICY_MASTER, "previousPremium", Aggregation.NONE, ValueTransform.DIRECT, null),
            criterion("VEHICLE_MFG_COUNTRY", "Vehicle Manufacture Country", DataType.ENUM, "MOTOR",
                    SourceEntity.POLICY_MASTER, "vehicleMfgCountry", Aggregation.NONE, ValueTransform.DIRECT, null),
            criterion("USAGE_LOCATION_ZONE", "Usage Location Zone", DataType.ENUM, "MOTOR",
                    SourceEntity.POLICY_MASTER, "usageLocationZone", Aggregation.NONE, ValueTransform.DIRECT, null),

            // ---- Claims (within the look-back window) ----
            criterion("CLAIM_COUNT", "Claim Count", DataType.NUMERIC_RANGE, "ALL",
                    SourceEntity.CLAIMS, null, Aggregation.COUNT, ValueTransform.DIRECT, null),
            criterion("CLAIM_AMOUNT_PAID", "Total Claim Amount Paid", DataType.NUMERIC_RANGE, "ALL",
                    SourceEntity.CLAIMS, "amountPaid", Aggregation.SUM, ValueTransform.DIRECT, null),
            criterion("MAX_CLAIM_AMOUNT", "Largest Claim Amount Claimed", DataType.NUMERIC_RANGE, "ALL",
                    SourceEntity.CLAIMS, "amountClaimed", Aggregation.MAX, ValueTransform.DIRECT, null),

            // ---- Computed ----
            criterion("LOSS_RATIO", "Loss Ratio %", DataType.NUMERIC_RANGE, "ALL",
                    SourceEntity.COMPUTED, null, Aggregation.NONE, ValueTransform.DIRECT, "LOSS_RATIO")
        );

        List<Criteria> toInsert = defaults.stream()
                .filter(c -> !existing.contains(c.getCriteriaCode())).toList();
        if (!toInsert.isEmpty()) {
            criteriaRepo.saveAll(toInsert);
            log.info("Seeded {} default criteria into mst_criteria", toInsert.size());
        }
        return toInsert.stream().map(Criteria::getCriteriaCode).collect(Collectors.toSet());
    }

    /**
     * Products with no mapping get every active criterion. Products the admin has
     * already mapped keep their selection, and only receive criteria that were
     * newly inserted in this run (so a new criterion still shows in the dropdown).
     */
    private void seedProductMappings(Set<String> newCodes) {
        List<Criteria> active = criteriaRepo.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .filter(CriteriaSeeder::hasReadableSourceField)
                .toList();
        if (active.isEmpty()) return;

        for (ProductLookupDTO product : policyRepo.findDistinctProducts()) {
            List<ProductCriteriaMap> current = mapRepo.findByProductId(product.productId());
            Set<Integer> mappedIds = current.stream()
                    .map(m -> m.getCriteria().getCriteriaId()).collect(Collectors.toSet());

            List<Criteria> toMap = current.isEmpty()
                    ? active
                    : active.stream().filter(c -> newCodes.contains(c.getCriteriaCode())
                                               && !mappedIds.contains(c.getCriteriaId())).toList();
            if (toMap.isEmpty()) continue;

            Integer productCompanyId = policyRepo.findCompanyIdByProductId(product.productId());
            int seq = current.stream().map(ProductCriteriaMap::getDisplaySequence)
                    .filter(java.util.Objects::nonNull).max(Integer::compare).orElse(0) + 1;
            for (Criteria c : toMap) {
                mapRepo.save(ProductCriteriaMap.builder()
                        .productId(product.productId())
                        .productName(product.productName())
                        .companyId(productCompanyId)
                        .criteria(c)
                        .criteriaName(c.getCriteriaName())
                        .isMandatory(false)
                        .displaySequence(seq++)
                        .build());
            }
            log.info("Mapped {} criteria to product {} ({})", toMap.size(), product.productId(), product.productName());
        }
    }

    /**
     * True when the batch can actually read this criterion: COMPUTED / COUNT need no
     * field; otherwise the entity must have a getter for source_field. Prevents
     * auto-mapping a criterion (e.g. one pointing at a non-existent "policyTenure")
     * that would fail the renewal batch once a rule uses it.
     */
    private static boolean hasReadableSourceField(Criteria c) {
        String f = c.getSourceField();
        if (c.getSourceEntity() == SourceEntity.COMPUTED) return true;
        if (c.getSourceEntity() == SourceEntity.CLAIMS && c.getAggregation() == Aggregation.COUNT) return true;
        if (f == null || f.isBlank()) return false;
        Class<?> type = c.getSourceEntity() == SourceEntity.CLAIMS
                ? com.insurance.renewal.entity.Claim.class
                : com.insurance.renewal.entity.PolicyMasterRecord.class;
        String getter = "get" + Character.toUpperCase(f.charAt(0)) + f.substring(1);
        try {
            type.getMethod(getter);
            return true;
        } catch (NoSuchMethodException e) {
            log.warn("Not auto-mapping criterion {}: {} has no {}()", c.getCriteriaCode(), type.getSimpleName(), getter);
            return false;
        }
    }

    private static Criteria criterion(String code, String name, DataType type, String lob,
                                      SourceEntity src, String field, Aggregation agg,
                                      ValueTransform transform, String formulaKey) {
        return Criteria.builder()
                .criteriaCode(code).criteriaName(name).dataType(type).lobScope(lob)
                .sourceEntity(src).sourceField(field).aggregation(agg)
                .valueTransform(transform).computedFormulaKey(formulaKey)
                .isActive(true).build();
    }
}
