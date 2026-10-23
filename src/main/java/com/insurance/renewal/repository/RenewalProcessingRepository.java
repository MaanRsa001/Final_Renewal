package com.insurance.renewal.repository;
import com.insurance.renewal.entity.RenewalProcessing;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RenewalProcessingRepository extends JpaRepository<RenewalProcessing, Long> {
    List<RenewalProcessing> findByPolicy_PolicyIdOrderByBatchRunDateDesc(Long policyId);
    List<RenewalProcessing> findByResolutionStatus(RenewalProcessing.ResolutionStatus status);
    List<RenewalProcessing> findByPolicy_BranchIdAndResolutionStatus(Integer branchId, RenewalProcessing.ResolutionStatus status);
    List<RenewalProcessing> findByPolicy_AgentId(Integer agentId);
    List<RenewalProcessing> findByPolicy_BranchId(Integer branchId);
}
