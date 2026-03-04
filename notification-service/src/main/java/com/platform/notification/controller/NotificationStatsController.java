package com.platform.notification.controller;

import com.platform.notification.dto.NotificationStatsResponse;
import com.platform.notification.registry.SessionRegistry;
import com.platform.notification.sse.SseEmitterStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats")
public class NotificationStatsController {

    private final SessionRegistry sessionRegistry;
    private final SseEmitterStore sseEmitterStore;
    private final MeterRegistry meterRegistry;

    public NotificationStatsController(SessionRegistry sessionRegistry,
                                       SseEmitterStore sseEmitterStore,
                                       MeterRegistry meterRegistry) {
        this.sessionRegistry = sessionRegistry;
        this.sseEmitterStore = sseEmitterStore;
        this.meterRegistry = meterRegistry;
    }

    @GetMapping
    public ResponseEntity<NotificationStatsResponse> getStats() {
        int ws = sessionRegistry.getConnectedSessionCount();
        int sse = sseEmitterStore.getActiveConnectionCount();
        long events = 0;
        try {
            var counter = meterRegistry.find("notifications.pushed.total").counter();
            if (counter != null) {
                events = (long) Math.max(0, counter.count());
            }
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok(new NotificationStatsResponse(ws, sse, events));
    }
}
