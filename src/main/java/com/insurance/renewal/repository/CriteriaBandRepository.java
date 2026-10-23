package com.insurance.renewal.repository;
import com.insurance.renewal.entity.CriteriaBand;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CriteriaBandRepository extends JpaRepository<CriteriaBand, Integer> {
    List<CriteriaBand> findByCriteria_CriteriaId(Integer criteriaId);
}
