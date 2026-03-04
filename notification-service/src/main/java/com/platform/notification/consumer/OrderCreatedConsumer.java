package com.platform.notification.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.notification.broadcast.DashboardRefreshBroadcaster;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes order.created so the dashboard can refresh counts and list in real time during bulk ingestion.
 */
@Component
public class OrderCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedConsumer.class);

    private final DashboardRefreshBroadcaster dashboardRefreshBroadcaster;
    private final ObjectMapper objectMapper;

    public OrderCreatedConsumer(DashboardRefreshBroadcaster dashboardRefreshBroadcaster, ObjectMapper objectMapper) {
        this.dashboardRefreshBroadcaster = dashboardRefreshBroadcaster;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "order.created",
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String payload = record.value();
            JsonNode node = objectMapper.readTree(payload);
            if (node.has("orderId")) {
                log.trace("Order created, requesting dashboard refresh");
            }
            dashboardRefreshBroadcaster.requestRefresh();
        } catch (Exception e) {
            log.warn("Failed to process order.created: {}", e.getMessage());
        }
        ack.acknowledge();
    }
}
