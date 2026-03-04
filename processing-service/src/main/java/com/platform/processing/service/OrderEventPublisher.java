package com.platform.processing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    public static final String TOPIC_ORDER_STATUS_UPDATED = "order.status.updated";
    public static final String TOPIC_PAYMENT_PROCESSED = "payment.processed";
    public static final String TOPIC_PAYMENT_FAILED = "payment.failed";
    public static final String TOPIC_COMPLIANCE_CHECKED = "compliance.checked";
    public static final String TOPIC_ORDER_APPROVED = "order.approved";
    public static final String TOPIC_ORDER_SHIPPED = "order.shipped";
    public static final String TOPIC_ORDER_CANCELLED = "order.cancelled";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String topic, String key, String payload) {
        kafkaTemplate.send(topic, key, payload);
        log.info("Published to {}: key={}", topic, key);
    }

    public void publishStatusUpdated(String orderId, String fromStatus, String toStatus) {
        String payload = String.format("{\"orderId\":\"%s\",\"fromStatus\":\"%s\",\"toStatus\":\"%s\",\"timestamp\":\"%s\"}",
                orderId, fromStatus != null ? fromStatus : "", toStatus, java.time.Instant.now());
        publish(TOPIC_ORDER_STATUS_UPDATED, orderId, payload);
    }
}
