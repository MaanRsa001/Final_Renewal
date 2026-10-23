package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/**
 * A rule carries its own matching criteria rather than pointing at a band row:
 * numeric criteria use rangeFrom/rangeTo, enum criteria use enumValue. This lets
 * each rule set version tune its own thresholds while mst_criteria_band stays the
 * shared reference set the business works to.
 */
@Entity @Table(name = "rule_definition")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RuleDefinition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer ruleDefId;

    @ManyToOne @JoinColumn(name = "rule_set_id", nullable = false)
    private RuleSet ruleSet;

    @ManyToOne @JoinColumn(name = "criteria_id", nullable = false)
    private Criteria criteria;

    @Column(name = "criteria_name", length = 100)
    private String criteriaName;

    /** Inclusive lower/upper bound for NUMERIC_RANGE criteria; null for enum rules. */
    private BigDecimal rangeFrom;
    private BigDecimal rangeTo;

    /** Exact stored code for ENUM criteria; null for numeric rules. */
    private String enumValue;

    @Column(nullable = false)
    private BigDecimal impactPct;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    @Builder.Default
    private ForceAction forceAction = ForceAction.NONE;

    @Builder.Default
    private Integer priorityOrder = 1;
    @Builder.Default
    private Boolean isActive = true;

    /** True when this rule matches on a numeric range rather than an enum code. */
    @Transient
    public boolean isRangeRule() {
        return rangeFrom != null && rangeTo != null;
    }

    public enum ForceAction { NONE, FORCE_REFER, FORCE_REJECT, FORCE_APPROVE }
}
