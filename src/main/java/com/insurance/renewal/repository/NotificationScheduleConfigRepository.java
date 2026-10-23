package com.insurance.renewal.repository;
import com.insurance.renewal.entity.NotificationScheduleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface NotificationScheduleConfigRepository extends JpaRepository<NotificationScheduleConfig, Integer> {
    List<NotificationScheduleConfig> findByProductId(Integer productId);
}
