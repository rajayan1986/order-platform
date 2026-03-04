package com.platform.processing.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.processing.saga.SagaOrchestrator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);
    private static final String TOPIC_ORDER_CREATED = "order.created";
    private static final String TOPIC_DLQ = "order.dlq";
    private static final int MAX_RETRIES = 3;
    private static final long[] BACKOFF_MS = {500, 1000, 2000};

    private final SagaOrchestrator sagaOrchestrator;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter dlqMessagesTotal;

    public OrderEventConsumer(SagaOrchestrator sagaOrchestrator,
                              KafkaTemplate<String, String> kafkaTemplate,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.sagaOrchestrator = sagaOrchestrator;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.dlqMessagesTotal = meterRegistry.counter("dlq.messages.total");
    }

    @KafkaListener(
            topics = TOPIC_ORDER_CREATED,
            groupId = "processing-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String payload = record.value();
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                JsonNode node = objectMapper.readTree(payload);
                JsonNode orderIdNode = node.has("orderId") ? node.get("orderId") : node.get("id");
                if (orderIdNode == null) {
                    log.error("Missing orderId in payload: {}", payload);
                    ack.acknowledge();
                    return;
                }
                UUID orderId = UUID.fromString(orderIdNode.asText());
                log.info("Consumed order.created: orderId={}, attempt={}", orderId, attempt);
                sagaOrchestrator.processOrder(orderId);
                ack.acknowledge();
                return;
            } catch (Exception e) {
                lastException = e;
                log.warn("Processing failed (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(BACKOFF_MS[attempt - 1]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        if (lastException != null) {
            sendToDlq(record, lastException);
        }
        ack.acknowledge();
    }

    private void sendToDlq(ConsumerRecord<String, String> record, Exception cause) {
        try {
            String dlqPayload = String.format("{\"originalTopic\":\"%s\",\"partition\":%d,\"offset\":%d,\"key\":\"%s\",\"payload\":%s,\"error\":\"%s\"}",
                    record.topic(), record.partition(), record.offset(),
                    record.key() != null ? record.key() : "",
                    record.value(),
                    cause.getMessage().replace("\"", "\\\""));
            kafkaTemplate.send(TOPIC_DLQ, record.key() != null ? record.key() : "dlq", dlqPayload);
            dlqMessagesTotal.increment();
            log.error("Message routed to DLQ: topic={}, partition={}, offset={}", record.topic(), record.partition(), record.offset());
        } catch (Exception ex) {
            log.error("Failed to send to DLQ", ex);
        }
    }
}
