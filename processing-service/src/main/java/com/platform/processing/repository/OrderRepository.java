package com.platform.processing.repository;

import com.platform.processing.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    Optional<OrderEntity> findById(UUID id);

    @Modifying
    @Query("UPDATE OrderEntity o SET o.status = :status, o.updatedAt = :updatedAt WHERE o.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") String status, @Param("updatedAt") Instant updatedAt);

    long countByStatus(String status);

    long countByUpdatedAtGreaterThanEqual(Instant instant);
}
