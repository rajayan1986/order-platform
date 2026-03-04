package com.platform.processing.repository;

import com.platform.processing.entity.OrderStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderStatusRepository extends JpaRepository<OrderStatusHistoryEntity, UUID> {

    List<OrderStatusHistoryEntity> findByOrderIdOrderByChangedAtAsc(UUID orderId);

    boolean existsByOrderIdAndToStatus(UUID orderId, String toStatus);
}
