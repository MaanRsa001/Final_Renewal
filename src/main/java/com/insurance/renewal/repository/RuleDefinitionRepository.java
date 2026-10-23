package com.insurance.renewal.repository;
import com.insurance.renewal.entity.RuleDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RuleDefinitionRepository extends JpaRepository<RuleDefinition, Integer> {
    List<RuleDefinition> findByRuleSet_RuleSetId(Integer ruleSetId);
}
