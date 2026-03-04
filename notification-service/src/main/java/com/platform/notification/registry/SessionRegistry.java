package com.platform.notification.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);
    private static final String KEY_PREFIX = "ws:sessions:";

    private final RedisTemplate<String, String> redisTemplate;
    private final int sessionTtlMinutes;
    private final ConcurrentHashMap<String, Set<String>> sessionToOrderIds = new ConcurrentHashMap<>();

    public SessionRegistry(RedisTemplate<String, String> redisTemplate,
                           @Value("${app.websocket.session-ttl-minutes:10}") int sessionTtlMinutes) {
        this.redisTemplate = redisTemplate;
        this.sessionTtlMinutes = sessionTtlMinutes;
    }

    public void register(String sessionId, String orderId) {
        String key = KEY_PREFIX + orderId;
        redisTemplate.opsForHash().put(key, sessionId, String.valueOf(System.currentTimeMillis()));
        redisTemplate.expire(key, java.time.Duration.ofMinutes(sessionTtlMinutes));
        sessionToOrderIds.compute(sessionId, (k, v) -> {
            Set<String> set = v != null ? v : ConcurrentHashMap.newKeySet();
            set.add(orderId);
            return set;
        });
        log.debug("Registered session {} for orderId {}", sessionId, orderId);
    }

    public void unregister(String sessionId) {
        Set<String> orderIds = sessionToOrderIds.remove(sessionId);
        if (orderIds != null) {
            for (String orderId : orderIds) {
                String key = KEY_PREFIX + orderId;
                redisTemplate.opsForHash().delete(key, sessionId);
            }
            log.debug("Unregistered session {} from {} orders", sessionId, orderIds.size());
        }
    }

    public boolean hasSessionsForOrder(String orderId) {
        Long size = redisTemplate.opsForHash().size(KEY_PREFIX + orderId);
        return size != null && size > 0;
    }

    public void refreshTtl(String orderId) {
        redisTemplate.expire(KEY_PREFIX + orderId, java.time.Duration.ofMinutes(sessionTtlMinutes));
    }

    /** Number of WebSocket sessions currently registered (for dashboard stats). */
    public int getConnectedSessionCount() {
        return sessionToOrderIds.size();
    }
}
