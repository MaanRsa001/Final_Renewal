package com.insurance.renewal.repository;
import com.insurance.renewal.entity.ProductCriteriaMap;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProductCriteriaMapRepository extends JpaRepository<ProductCriteriaMap, Integer> {
    List<ProductCriteriaMap> findByProductId(Integer productId);
    void deleteByProductId(Integer productId);
    List<ProductCriteriaMap> findByProductIdAndCompanyId(Integer productId, Integer companyId);
    void deleteByProductIdAndCompanyId(Integer productId, Integer companyId);
    void deleteByProductIdAndCompanyIdIsNull(Integer productId);
}
