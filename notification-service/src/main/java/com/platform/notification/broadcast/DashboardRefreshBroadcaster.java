package com.platform.notification.broadcast;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Broadcasts a "refresh" signal to /topic/dashboard/refresh so the dashboard can refetch stats and order list.
 * Throttled to avoid flooding during bulk ingestion (max once per 800ms).
 */
@Component
public class DashboardRefreshBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(DashboardRefreshBroadcaster.class);
    private static final String DESTINATION = "/topic/dashboard/refresh";
    private static final long THROTTLE_MS = 800;

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private volatile long lastSentAt;

    public DashboardRefreshBroadcaster(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Request a dashboard refresh broadcast. Throttled so we send at most once per THROTTLE_MS.
     */
    public void requestRefresh() {
        long now = System.currentTimeMillis();
        if (now - lastSentAt < THROTTLE_MS) {
            return;
        }
        lastSentAt = now;
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "event", "refresh",
                    "ts", Instant.now().toString()
            ));
            messagingTemplate.convertAndSend(DESTINATION, payload);
            log.trace("Broadcast dashboard refresh");
        } catch (Exception e) {
            log.warn("Failed to broadcast dashboard refresh: {}", e.getMessage());
        }
    }
}
