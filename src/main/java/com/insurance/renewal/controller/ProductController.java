package com.insurance.renewal.controller;

import com.insurance.renewal.dto.ProductLookupDTO;
import com.insurance.renewal.entity.LossRatioConfig;
import com.insurance.renewal.entity.ProductCriteriaMap;
import com.insurance.renewal.repository.CriteriaRepository;
import com.insurance.renewal.repository.MstLossRatioConfigRepository;
import com.insurance.renewal.repository.PolicyMasterRecordRepository;
import com.insurance.renewal.repository.ProductCriteriaMapRepository;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Products are read from policy_master (mst_product and
 * mst_product_criteria_map are no longer used), so the list is read-only. The
 * loss-ratio config keeps working against the plain product_id.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
	private final PolicyMasterRecordRepository policyRepo;
	private final ProductCriteriaMapRepository criteriaMapRepo;
	private final CriteriaRepository criteriaRepo;
	private final MstLossRatioConfigRepository lossRatioRepo;

	@GetMapping
	public List<ProductLookupDTO> findAll() {
		return policyRepo.findDistinctProducts();
	}

	@GetMapping("/{id}")
	public ResponseEntity<ProductLookupDTO> findOne(@PathVariable Integer id) {
		return policyRepo.findDistinctProducts().stream().filter(p -> id.equals(p.productId())).findFirst()
				.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	// ---- Product <-> Criteria mapping (drives which rule-engine criteria this
	// product evaluates) ----

	@GetMapping("/{productId}/criteria")
	public List<Integer> getCriteriaMapping(@PathVariable Integer productId,
			@RequestParam(required = false) Integer companyId) {
		List<ProductCriteriaMap> maps = companyId == null
				? criteriaMapRepo.findByProductId(productId)
				: criteriaMapRepo.findByProductIdAndCompanyId(productId, companyId);
		return maps.stream().map(m -> m.getCriteria().getCriteriaId()).toList();
	}

	/**
	 * Replaces the full mapping for this product - simplest correct semantics for a
	 * small admin list.
	 */
	@PutMapping("/{productId}/criteria")
	@Transactional
	public ResponseEntity<List<ProductCriteriaMap>> saveCriteriaMapping(@PathVariable Integer productId,
	        @RequestBody Map<String, Object> body) {
	    // body: { "criteriaIds": [1,2,3], "companyId": 100002 }  (companyId optional)
	    List<Integer> criteriaIds = new ArrayList<>();
	    if (body.get("criteriaIds") instanceof List<?> ids)
	        for (Object o : ids) if (o != null) criteriaIds.add(Integer.valueOf(o.toString()));
	    Integer companyId = null;
	    Object cid = body.get("companyId");
	    if (cid != null && !cid.toString().isBlank()) companyId = Integer.valueOf(cid.toString().trim());

	    String productName = lookupProductName(productId);

	    if (companyId == null) {
	        // not sent by the screen: take it from policy_master (company with most policies of this product)
	        criteriaMapRepo.deleteByProductId(productId);   // replace the whole product mapping, as before
	        companyId = policyRepo.findCompanyIdsByProduct(productId).stream().findFirst().orElse(null);
	    } else {
	        criteriaMapRepo.deleteByProductIdAndCompanyId(productId, companyId);
	        criteriaMapRepo.deleteByProductIdAndCompanyIdIsNull(productId);   // old rows saved before company_id existed
	    }
	    final Integer mapCompanyId = companyId;
	    List<ProductCriteriaMap> saved = new ArrayList<>();
	    int seq = 1;
	    for (Integer criteriaId : criteriaIds) {
	        var criteria = criteriaRepo.findById(criteriaId)
	                .orElseThrow(() -> new NoSuchElementException("Criteria not found: " + criteriaId));
	        saved.add(criteriaMapRepo.save(ProductCriteriaMap.builder().companyId(mapCompanyId).productId(productId).criteria(criteria)
	                .isMandatory(true).displaySequence(seq++).productName(productName)
	                .criteriaName(criteria.getCriteriaName()).build()));
	    }
	    return ResponseEntity.ok(saved);
	}

	// ---- Loss ratio basis config (premium-paid vs sum-insured, product-wise) ----

	@GetMapping("/{productId}/loss-ratio-config")
	public ResponseEntity<LossRatioConfig> getLossRatioConfig(@PathVariable Integer productId) {
		return lossRatioRepo.findByProductId(productId).stream().filter(c -> Boolean.TRUE.equals(c.getIsActive()))
				.findFirst().map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
	}

	/**
	 * Upserts: updates the product's current active config if one exists, else
	 * creates it.
	 */
	@PutMapping("/{productId}/loss-ratio-config")
	@Transactional
	public LossRatioConfig saveLossRatioConfig(@PathVariable Integer productId,
			@Valid @RequestBody LossRatioConfig req) {
		LossRatioConfig existing = lossRatioRepo.findByProductId(productId).stream()
				.filter(c -> Boolean.TRUE.equals(c.getIsActive())).findFirst().orElse(null);
		String productName = (req.getProductName() != null && !req.getProductName().isBlank())
				? req.getProductName().trim()
				: lookupProductName(productId);

		if (existing != null) {
			if (productName != null)
				existing.setProductName(productName);
			existing.setBasisType(req.getBasisType());
			existing.setClaimLookbackYears(req.getClaimLookbackYears());
			existing.setEffectiveFrom(req.getEffectiveFrom());
			existing.setEffectiveTo(req.getEffectiveTo());
			return lossRatioRepo.save(existing);
		}
		req.setConfigId(null);
		req.setProductId(productId);
		req.setProductName(productName);
		if (req.getEffectiveFrom() == null)
			req.setEffectiveFrom(LocalDate.now());
		req.setIsActive(true);
		return lossRatioRepo.save(req);
	}

	private String lookupProductName(Integer productId) {
		return policyRepo.findDistinctProducts().stream().filter(p -> productId.equals(p.productId()))
				.map(ProductLookupDTO::productName).filter(n -> n != null && !n.isBlank()).map(String::trim).findFirst()
				.orElse(null);
	}
}
