package com.platform.notification.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseEmitterStore {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterStore.class);

    private final Map<String, Map<String, SseEmitter>> orderEmitters = new ConcurrentHashMap<>();

    public SseEmitter add(String orderId, String emitterId, SseEmitter emitter) {
        emitter.onCompletion(() -> remove(orderId, emitterId));
        emitter.onTimeout(() -> remove(orderId, emitterId));
        emitter.onError(e -> remove(orderId, emitterId));
        orderEmitters.computeIfAbsent(orderId, k -> new ConcurrentHashMap<>()).put(emitterId, emitter);
        return emitter;
    }

    public void remove(String orderId, String emitterId) {
        Map<String, SseEmitter> map = orderEmitters.get(orderId);
        if (map != null) {
            map.remove(emitterId);
            if (map.isEmpty()) {
                orderEmitters.remove(orderId);
            }
        }
    }

    public void sendToOrder(String orderId, Object data) {
        Map<String, SseEmitter> map = orderEmitters.get(orderId);
        if (map == null) return;
        map.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().data(data));
            } catch (IOException e) {
                log.warn("SSE send failed for orderId={} emitterId={}: {}", orderId, id, e.getMessage());
                remove(orderId, id);
            }
        });
    }

    public void sendToOrderWithId(String orderId, String eventId, Object data) {
        Map<String, SseEmitter> map = orderEmitters.get(orderId);
        if (map == null) return;
        map.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().id(eventId).data(data));
            } catch (IOException e) {
                log.warn("SSE send failed for orderId={} emitterId={}: {}", orderId, id, e.getMessage());
                remove(orderId, id);
            }
        });
    }

    public int getActiveConnectionCount() {
        return orderEmitters.values().stream().mapToInt(Map::size).sum();
    }

    @Scheduled(fixedRate = 60000)
    public void cleanup() {
        orderEmitters.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
}
