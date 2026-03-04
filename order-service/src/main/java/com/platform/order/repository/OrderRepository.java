package com.platform.order.repository;

import com.platform.order.domain.Order;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface OrderRepository extends R2dbcRepository<Order, UUID> {

    Flux<Order> findAllByOrderByCreatedAtDesc();

    Mono<Long> countByStatus(String status);

    Mono<Long> countByCreatedAtGreaterThanEqual(Instant instant);
}
