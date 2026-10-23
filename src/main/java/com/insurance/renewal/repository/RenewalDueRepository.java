package com.insurance.renewal.repository;
import com.insurance.renewal.entity.RenewalDue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RenewalDueRepository extends JpaRepository<RenewalDue, Long> {
    List<RenewalDue> findByPolicy_PolicyId(Long policyId);
    List<RenewalDue> findByCurrentStatus(RenewalDue.Status status);
}
