package com.insurance.renewal.service;

import com.insurance.renewal.entity.Claim;
import com.insurance.renewal.entity.Criteria;
import com.insurance.renewal.entity.PolicyMasterRecord;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;

/**
 * Resolves a criterion's value at batch time from the field it declares
 * (Criteria.sourceEntity / sourceField / aggregation / valueTransform),
 * instead of a hardcoded switch on criteriaCode. This is what makes
 * "criteria are data, not code" actually true for the rule engine, not just
 * for the Master Setup UI: a new criterion built on an already-supported
 * field (another PolicyMasterRecord column, another Claim aggregation) requires
 * zero changes here.
 *
 * POLICY_MASTER and CLAIMS fields are read via reflection on the JPA
 * entities' getters, using sourceField as the camelCase property name -
 * this is the same value that appears in the Master Setup "source field"
 * dropdown (see CriteriaController#sourceFields), so what an admin picks in
 * the UI is exactly what gets invoked here. COMPUTED criteria (currently
 * just loss ratio, which combines claims with a premium/sum-insured basis
 * and can't be reduced to a single column) are resolved from a small named
 * formula map the batch service builds per policy.
 */
@Service
public class RenewalCriteriaValueResolver {

    /** Numeric value for a NUMERIC_RANGE criterion, or null if criteria requires enum matching instead. */
    public BigDecimal resolveNumeric(Criteria criteria, PolicyMasterRecord policy, List<Claim> claimsInWindow,
                                      LocalDate asOf, Map<String, BigDecimal> computedValues) {
        return switch (criteria.getSourceEntity()) {
            case POLICY_MASTER -> resolvePolicyNumeric(criteria, policy, asOf);
            case CLAIMS -> resolveClaimsAggregate(criteria, claimsInWindow);
            case COMPUTED -> computedValues.get(criteria.getComputedFormulaKey());
        };
    }

    /** Stored code for an ENUM criterion (e.g. vehicleMfgCountry, vehicleusageDesc). */
    public String resolveEnum(Criteria criteria, PolicyMasterRecord policy) {
        if (criteria.getSourceEntity() != Criteria.SourceEntity.POLICY_MASTER) {
            throw new IllegalStateException("ENUM criteria must be sourced from POLICY_MASTER: " + criteria.getCriteriaCode());
        }
        Object raw = invokeGetter(policy, criteria.getSourceField());
        return raw == null ? null : raw.toString();
    }

    private BigDecimal resolvePolicyNumeric(Criteria criteria, PolicyMasterRecord policy, LocalDate asOf) {
        Object raw = invokeGetter(policy, criteria.getSourceField());
        if (raw == null) return null;

        if (criteria.getValueTransform() == Criteria.ValueTransform.AGE_IN_YEARS) {
            if (!(raw instanceof LocalDate date)) {
                throw new IllegalStateException("AGE_IN_YEARS transform requires a date field, got "
                        + raw.getClass().getSimpleName() + " for " + criteria.getCriteriaCode());
            }
            return BigDecimal.valueOf(Period.between(date, asOf).getYears());
        }
        return toBigDecimal(raw, criteria);
    }

    private BigDecimal resolveClaimsAggregate(Criteria criteria, List<Claim> claims) {
        return switch (criteria.getAggregation()) {
            case COUNT -> BigDecimal.valueOf(claims.size());
            case SUM -> claims.stream().map(c -> toBigDecimal(invokeGetter(c, criteria.getSourceField()), criteria))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            case AVG -> claims.isEmpty() ? BigDecimal.ZERO
                    : claims.stream().map(c -> toBigDecimal(invokeGetter(c, criteria.getSourceField()), criteria))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(claims.size()), 4, RoundingMode.HALF_UP);
            case MAX -> claims.stream().map(c -> toBigDecimal(invokeGetter(c, criteria.getSourceField()), criteria))
                    .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            case MIN -> claims.stream().map(c -> toBigDecimal(invokeGetter(c, criteria.getSourceField()), criteria))
                    .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            case NONE -> throw new IllegalStateException("CLAIMS-sourced criteria must declare an aggregation: " + criteria.getCriteriaCode());
        };
    }

    private BigDecimal toBigDecimal(Object raw, Criteria criteria) {
        if (raw == null) return BigDecimal.ZERO;
        if (raw instanceof BigDecimal bd) return bd;
        if (raw instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        throw new IllegalStateException("Field '" + criteria.getSourceField() + "' on " + criteria.getCriteriaCode()
                + " is not numeric (got " + raw.getClass().getSimpleName() + ") - check the criterion's source field mapping.");
    }

    /** Reflects a JavaBean getter (property "driverDob" -> method "getDriverDob") on the given entity. */
    private Object invokeGetter(Object target, String property) {
        if (property == null) {
            throw new IllegalStateException("Criterion has no source field mapped for " + target.getClass().getSimpleName());
        }
        String getterName = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        try {
            Method m = target.getClass().getMethod(getterName);
            return m.invoke(target);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Criterion source field '" + property + "' does not exist on "
                    + target.getClass().getSimpleName() + " (expected getter " + getterName + "()). "
                    + "Check Master Setup > Applicable Criteria > source field mapping.", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read field '" + property + "' on " + target.getClass().getSimpleName(), e);
        }
    }
}
