package com.platform.order;

import com.platform.order.domain.OutboxEvent;
import com.platform.order.kafka.OrderEventProducer;
import com.platform.order.repository.OutboxRepository;
import com.platform.order.service.OutboxRelay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private OrderEventProducer orderEventProducer;

    @Test
    void testRelayPublishesUnpublishedEvents() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(eventId, aggregateId, "Order", "order.created", "{\"id\":\"" + aggregateId + "\"}", null, Instant.now());

        when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(Flux.just(event));
        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> reactor.core.publisher.Mono.just(inv.getArgument(0)));

        OutboxRelay relay = new OutboxRelay(outboxRepository, orderEventProducer);
        relay.relayUnpublishedEvents();

        verify(orderEventProducer).publish(eq("order.created"), eq("{\"id\":\"" + aggregateId + "\"}"), eq(aggregateId.toString()));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getPublishedAt()).isNotNull();
    }

    @Test
    void testRelayRetriesOnKafkaFailure() throws Exception {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(eventId, UUID.randomUUID(), "Order", "order.created", "{}", null, Instant.now());

        when(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(Flux.just(event));
        doThrow(new RuntimeException("Kafka unavailable")).when(orderEventProducer).publish(anyString(), anyString(), anyString());

        OutboxRelay relay = new OutboxRelay(outboxRepository, orderEventProducer);
        relay.relayUnpublishedEvents();
        Thread.sleep(500);

        verify(orderEventProducer).publish(anyString(), anyString(), anyString());
        verify(outboxRepository, never()).save(any());
    }
}
