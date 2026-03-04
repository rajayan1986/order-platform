package com.platform.order.repository;

import com.platform.order.domain.OrderItem;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface OrderItemRepository extends R2dbcRepository<OrderItem, UUID> {

    Flux<OrderItem> findByOrderId(UUID orderId);
}
