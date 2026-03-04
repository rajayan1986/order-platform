package com.platform.order.service;

import com.platform.order.domain.OutboxEvent;
import com.platform.order.kafka.OrderEventProducer;
import com.platform.order.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outboxRepository;
    private final OrderEventProducer orderEventProducer;

    public OutboxRelay(OutboxRepository outboxRepository, OrderEventProducer orderEventProducer) {
        this.outboxRepository = outboxRepository;
        this.orderEventProducer = orderEventProducer;
    }

    @Scheduled(fixedRate = 1000)
    public void relayUnpublishedEvents() {
        outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()
                .flatMap(event -> Mono.fromCallable(() -> {
                            orderEventProducer.publish(event.getEventType(), event.getPayload(), event.getAggregateId().toString());
                            return event;
                        }).subscribeOn(Schedulers.boundedElastic())
                        .flatMap(ev -> {
                            ev.setPublishedAt(Instant.now());
                            return outboxRepository.save(ev)
                                    .doOnSuccess(saved -> log.info("Outbox event published: id={}, eventType={}", saved.getId(), saved.getEventType()));
                        })
                        .onErrorResume(e -> {
                            log.warn("Failed to publish outbox event id={}, will retry: {}", event.getId(), e.getMessage());
                            return Mono.empty();
                        })
                )
                .subscribe();
    }
}
