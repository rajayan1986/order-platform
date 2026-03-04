package com.platform.order.repository;

import com.platform.order.domain.IdempotencyKey;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface IdempotencyKeyRepository extends R2dbcRepository<IdempotencyKey, String> {

    Mono<IdempotencyKey> findByKey(String key);
}
