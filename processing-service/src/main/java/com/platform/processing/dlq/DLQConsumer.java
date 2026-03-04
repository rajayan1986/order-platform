package com.platform.processing.dlq;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class DLQConsumer {

    private static final Logger log = LoggerFactory.getLogger(DLQConsumer.class);
    private static final String TOPIC_DLQ = "order.dlq";

    private final Counter dlqMessagesTotal;

    public DLQConsumer(MeterRegistry meterRegistry) {
        this.dlqMessagesTotal = meterRegistry.counter("dlq.messages.total");
    }

    @KafkaListener(
            topics = TOPIC_DLQ,
            groupId = "processing-service-dlq-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.error("DLQ message received: topic={}, partition={}, offset={}, key={}, payload={}",
                record.topic(), record.partition(), record.offset(), record.key(), record.value());
        dlqMessagesTotal.increment();
        ack.acknowledge();
    }
}
