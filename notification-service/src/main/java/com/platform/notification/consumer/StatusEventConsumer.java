package com.platform.notification.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.notification.broadcast.DashboardRefreshBroadcaster;
import com.platform.notification.registry.SessionRegistry;
import com.platform.notification.sse.SseEmitterStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class StatusEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(StatusEventConsumer.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final SessionRegistry sessionRegistry;
    private final SseEmitterStore sseEmitterStore;
    private final ObjectMapper objectMapper;
    private final DashboardRefreshBroadcaster dashboardRefreshBroadcaster;
    private final Counter notificationsPushedTotal;

    public StatusEventConsumer(SimpMessagingTemplate messagingTemplate,
                               SessionRegistry sessionRegistry,
                               SseEmitterStore sseEmitterStore,
                               ObjectMapper objectMapper,
                               DashboardRefreshBroadcaster dashboardRefreshBroadcaster,
                               MeterRegistry meterRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.sessionRegistry = sessionRegistry;
        this.sseEmitterStore = sseEmitterStore;
        this.objectMapper = objectMapper;
        this.dashboardRefreshBroadcaster = dashboardRefreshBroadcaster;
        this.notificationsPushedTotal = meterRegistry.counter("notifications.pushed.total");
    }

    @KafkaListener(
            topics = {"order.status.updated", "order.shipped", "order.cancelled"},
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String payload = record.value();
            JsonNode node = objectMapper.readTree(payload);
            String orderId = node.has("orderId") ? node.get("orderId").asText() : record.key();
            if (orderId == null) {
                log.warn("No orderId in message, skipping");
                ack.acknowledge();
                return;
            }

            sessionRegistry.refreshTtl(orderId);

            String destination = "/topic/orders/" + orderId;
            messagingTemplate.convertAndSend(destination, payload);
            sseEmitterStore.sendToOrderWithId(orderId, String.valueOf(record.offset()), payload);
            notificationsPushedTotal.increment();
            dashboardRefreshBroadcaster.requestRefresh();
            log.info("Pushed status update for orderId={} to WebSocket and SSE", orderId);
        } catch (Exception e) {
            log.error("Failed to process status event: {}", e.getMessage());
        }
        ack.acknowledge();
    }
}
