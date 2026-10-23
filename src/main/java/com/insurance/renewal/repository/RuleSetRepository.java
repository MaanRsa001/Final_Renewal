package com.insurance.renewal.repository;

import com.insurance.renewal.entity.RuleSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RuleSetRepository extends JpaRepository<RuleSet, Integer> {

    List<RuleSet> findByProductId(Integer productId);

    Optional<RuleSet>
    findFirstByProductIdAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByEffectiveFromDesc(
            Integer productId,
            RuleSet.Status status,
            LocalDate effectiveDate1,
            LocalDate effectiveDate2
    );
}