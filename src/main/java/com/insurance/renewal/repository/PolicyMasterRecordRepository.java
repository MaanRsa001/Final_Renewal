package com.insurance.renewal.repository;

import com.insurance.renewal.dto.AgentLookupDTO;
import com.insurance.renewal.dto.BranchLookupDTO;
import com.insurance.renewal.dto.CustomerLookupDTO;
import com.insurance.renewal.dto.ProductLookupDTO;
import com.insurance.renewal.entity.PolicyMasterRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The single repository for policy_master. Product / branch / agent / customer look-ups are derived from
 * policy_master itself (product_id + product_name, branch_id + branch_name, ...), so the mst_product,
 * mst_branch, mst_agent and mst_customer tables are not needed.
 */
public interface PolicyMasterRecordRepository extends JpaRepository<PolicyMasterRecord, Long> {

    boolean existsByPolicyNo(String policyNo);

    Optional<PolicyMasterRecord> findByPolicyNo(String policyNo);

    List<PolicyMasterRecord> findByMobileNo(String mobileNo);

    // ---- broker API (always scoped by broker_code) ----
    Optional<PolicyMasterRecord> findByBrokerCodeAndPolicyNo(String brokerCode, String policyNo);

    Page<PolicyMasterRecord> findByBrokerCodeAndPolicyStatusAndPolicyExpiryDateBetween(
            String brokerCode, String policyStatus, LocalDate from, LocalDate to, Pageable pageable);

    List<PolicyMasterRecord> findByBranchId(Integer branchId);

    List<PolicyMasterRecord> findByAgentId(Integer agentId);

    // ---- look-ups derived from policy_master ----

//    @Query("select distinct new com.insurance.renewal.dto.ProductLookupDTO(p.productId, p.productName) "
//         + "from PolicyMasterRecord p where p.productId is not null order by p.productId")
//    List<ProductLookupDTO> findDistinctProducts();

    /** Company that owns this product in policy_master (used to stamp company_id on criteria bands / mappings). */
    @Query("select max(p.companyId) from PolicyMasterRecord p where p.productId = :productId")
    Integer findCompanyIdByProductId(@Param("productId") Integer productId);
    
    @Query("select new com.insurance.renewal.dto.ProductLookupDTO(p.productId, max(trim(p.productName))) "
    	     + "from PolicyMasterRecord p where p.productId is not null "
    	     + "group by p.productId order by p.productId")
    	List<ProductLookupDTO> findDistinctProducts();
    
    /** Product ids in policy_master, most policies first (fallback when the screen sends no product). */
    @Query("select p.productId from PolicyMasterRecord p where p.productId is not null "
         + "group by p.productId order by count(p) desc, p.productId")
    List<Integer> findProductIdsByPolicyCount();

    /** Product ids in policy_master, most policies first (product_id fallback when the screen sends none). */
    @Query("select p.productId from PolicyMasterRecord p where p.productId is not null "
         + "group by p.productId order by count(p) desc, p.productId")
    List<Integer> findProductIdsByCount();

    /** Company ids that hold policies of this product, most policies first (company_id source for Master Setup rows). */
    @Query("select p.companyId from PolicyMasterRecord p where p.productId = :productId and p.companyId is not null "
         + "group by p.companyId order by count(p) desc, p.companyId")
    List<Integer> findCompanyIdsByProduct(@Param("productId") Integer productId);

    @Query("select distinct new com.insurance.renewal.dto.BranchLookupDTO(p.branchId, p.branchName) "
         + "from PolicyMasterRecord p where p.branchId is not null order by p.branchId")
    List<BranchLookupDTO> findDistinctBranches();

    @Query("select new com.insurance.renewal.dto.AgentLookupDTO(p.agentId, max(coalesce(p.agentName, cast(p.agentId as string))), p.branchId) "
         + "from PolicyMasterRecord p where p.agentId is not null group by p.agentId, p.branchId order by p.agentId")
    List<AgentLookupDTO> findDistinctAgents();

    @Query("select new com.insurance.renewal.dto.AgentLookupDTO(p.agentId, max(coalesce(p.agentName, cast(p.agentId as string))), p.branchId) "
         + "from PolicyMasterRecord p where p.agentId is not null and p.branchId = :branchId group by p.agentId, p.branchId order by p.agentId")
    List<AgentLookupDTO> findDistinctAgentsByBranch(@Param("branchId") Integer branchId);

    @Query("select distinct new com.insurance.renewal.dto.CustomerLookupDTO(p.customerId, p.customerName, p.mobileNo, p.email) "
         + "from PolicyMasterRecord p where p.customerId is not null order by p.customerId")
    List<CustomerLookupDTO> findDistinctCustomers();

    @Query("select distinct new com.insurance.renewal.dto.CustomerLookupDTO(p.customerId, p.customerName, p.mobileNo, p.email) "
         + "from PolicyMasterRecord p where p.customerId is not null and p.mobileNo = :mobileNo")
    List<CustomerLookupDTO> findDistinctCustomersByMobile(@Param("mobileNo") String mobileNo);
}
