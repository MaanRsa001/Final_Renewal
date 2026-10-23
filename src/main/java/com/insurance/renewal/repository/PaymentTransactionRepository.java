package com.insurance.renewal.repository;
import com.insurance.renewal.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    List<PaymentTransaction> findByPolicy_PolicyId(Long policyId);
    void deleteByProcessing_ProcessingId(Long processingId);

}
