package com.platform.order.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class OrderEventProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);
    public static final String TOPIC_ORDER_CREATED = "order.created";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Synchronous publish for use from OutboxRelay (scheduled task).
     */
    public void publish(String eventType, String payload, String key) {
        String topic = TOPIC_ORDER_CREATED;
        try {
            kafkaTemplate.send(topic, key, payload).get();
            log.info("Published event to {}: eventType={}, key={}", topic, eventType, key);
        } catch (Exception e) {
            log.error("Failed to publish event to {}: {}", topic, e.getMessage());
            throw new RuntimeException("Kafka publish failed", e);
        }
    }

    public CompletableFuture<SendResult<String, String>> publishAsync(String topic, String key, String payload) {
        return kafkaTemplate.send(topic, key, payload);
    }
}
