package com.insurance.renewal.repository;
import com.insurance.renewal.entity.LossRatioConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface MstLossRatioConfigRepository extends JpaRepository<LossRatioConfig, Integer> {
    List<LossRatioConfig> findByProductId(Integer productId);
}
