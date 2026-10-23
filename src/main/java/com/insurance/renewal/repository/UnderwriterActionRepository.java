package com.insurance.renewal.repository;
import com.insurance.renewal.entity.UnderwriterAction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface UnderwriterActionRepository extends JpaRepository<UnderwriterAction, Long> {
    List<UnderwriterAction> findByProcessing_ProcessingId(Long processingId);
    void deleteByProcessing_ProcessingId(Long processingId);
}
