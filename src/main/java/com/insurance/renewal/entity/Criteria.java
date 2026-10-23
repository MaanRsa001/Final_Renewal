package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A criterion in the rule engine's shared library. Crucially, each row declares
 * exactly which field it evaluates at batch time (sourceEntity + sourceField +
 * aggregation), so RenewalCriteriaValueResolver can resolve its value generically
 * by reflection instead of a hardcoded switch on criteriaCode. Adding a new
 * criterion that reads an already-exposed policy_master or claims field is a
 * pure data change - no code deployment.
 */
@Entity @Table(name = "mst_criteria")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Criteria {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer criteriaId;
    @Column(nullable = false, unique = true, length = 40)
    private String criteriaCode;
    @Column(nullable = false, length = 100)
    private String criteriaName;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private DataType dataType;
    @Builder.Default
    private String lobScope = "ALL";

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    @Builder.Default
    private SourceEntity sourceEntity = SourceEntity.POLICY_MASTER;

    /** JPA entity property name (camelCase) this criterion reads, e.g. "driverDob",
     *  "amountPaid". Null for COMPUTED criteria and for CLAIMS criteria using COUNT. */
    private String sourceField;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    @Builder.Default
    private Aggregation aggregation = Aggregation.NONE;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    @Builder.Default
    private ValueTransform valueTransform = ValueTransform.DIRECT;

    /** Only set when sourceEntity = COMPUTED - names which formula in
     *  RenewalCriteriaValueResolver to run (currently only "LOSS_RATIO"). */
    private String computedFormulaKey;

    @Builder.Default
    private Boolean isActive = true;

    public enum DataType { NUMERIC_RANGE, ENUM, LOOKUP }
    public enum SourceEntity { POLICY_MASTER, CLAIMS, COMPUTED }
    public enum Aggregation { NONE, COUNT, SUM, AVG, MAX, MIN }
    public enum ValueTransform { DIRECT, AGE_IN_YEARS }
}
