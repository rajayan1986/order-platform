package com.platform.notification.sse;

import com.platform.notification.service.OrderServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/sse")
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000;

    private final SseEmitterStore emitterStore;
    private final OrderServiceClient orderServiceClient;

    public SseController(SseEmitterStore emitterStore, OrderServiceClient orderServiceClient) {
        this.emitterStore = emitterStore;
        this.orderServiceClient = orderServiceClient;
    }

    @GetMapping(value = "/orders/{orderId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOrder(@PathVariable String orderId,
                                  @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String emitterId = UUID.randomUUID().toString();
        emitterStore.add(orderId, emitterId, emitter);

        if (lastEventId != null && !lastEventId.isBlank()) {
            try {
                String currentStatus = orderServiceClient.getOrderStatus(orderId);
                if (currentStatus != null) {
                    String eventId = "reconnect-" + System.currentTimeMillis();
                    String payload = "{\"status\":\"" + currentStatus + "\"}";
                    emitter.send(SseEmitter.event().id(eventId).name("status").data(payload));
                }
            } catch (Exception e) {
                log.warn("Failed to fetch order status on reconnect for orderId={}: {}", orderId, e.getMessage());
            }
        }

        log.info("SSE client connected for orderId={} emitterId={}", orderId, emitterId);
        return emitter;
    }
}
