package com.insurance.renewal.repository;
import com.insurance.renewal.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findByPolicy_PolicyId(Long policyId);
    List<NotificationLog> findByProcessing_ProcessingId(Long processingId);
    
    void deleteByProcessing_ProcessingId(Long processingId);
}
