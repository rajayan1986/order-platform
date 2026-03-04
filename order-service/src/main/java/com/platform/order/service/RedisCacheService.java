package com.platform.order.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class RedisCacheService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public RedisCacheService(
            @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<String> get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Mono<Boolean> set(String key, String value, long ttlSeconds) {
        return redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * Increment key and check if within limit. If key is new, set TTL to windowSeconds.
     * Returns true if count <= limit after increment, false otherwise.
     */
    public Mono<Boolean> incrementAndCheckLimit(String key, int limit, int windowSeconds) {
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(key, Duration.ofSeconds(windowSeconds))
                                .thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .map(count -> count <= limit);
    }
}
