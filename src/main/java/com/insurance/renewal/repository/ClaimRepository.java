package com.insurance.renewal.repository;
import com.insurance.renewal.entity.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ClaimRepository extends JpaRepository<Claim, Long> {
    List<Claim> findByPolicy_PolicyId(Long policyId);
}
