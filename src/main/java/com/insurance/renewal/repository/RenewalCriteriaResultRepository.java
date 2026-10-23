package com.insurance.renewal.repository;
import com.insurance.renewal.entity.RenewalCriteriaResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RenewalCriteriaResultRepository extends JpaRepository<RenewalCriteriaResult, Long> {
    List<RenewalCriteriaResult> findByProcessing_ProcessingId(Long processingId);
    void deleteByProcessing_ProcessingId(Long processingId);

}
