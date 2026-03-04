package com.platform.processing.steps;

import com.platform.processing.repository.OrderRepository;
import com.platform.processing.repository.OrderStatusRepository;
import com.platform.processing.service.OrderEventPublisher;
import com.platform.processing.entity.OrderStatusHistoryEntity;
import com.platform.processing.saga.SagaStepResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class PaymentStep {

    private static final Logger log = LoggerFactory.getLogger(PaymentStep.class);
    private static final String STEP_STATUS = "PAYMENT_VALIDATED";
    private static final String FAILED_STATUS = "PAYMENT_FAILED";

    private final OrderRepository orderRepository;
    private final OrderStatusRepository orderStatusRepository;
    private final OrderEventPublisher eventPublisher;

    public PaymentStep(OrderRepository orderRepository,
                       OrderStatusRepository orderStatusRepository,
                       OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderStatusRepository = orderStatusRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SagaStepResult execute(UUID orderId, String currentStatus) {
        if (orderStatusRepository.existsByOrderIdAndToStatus(orderId, STEP_STATUS)) {
            log.warn("Step PAYMENT_VALIDATION already executed for orderId={}, skipping", orderId);
            return SagaStepResult.success(STEP_STATUS);
        }
        boolean success = Math.random() < 0.9;
        if (success) {
            Instant now = Instant.now();
            orderRepository.updateStatus(orderId, STEP_STATUS, now);
            orderStatusRepository.save(createHistory(orderId, currentStatus, STEP_STATUS, "payment-step", now));
            eventPublisher.publish(OrderEventPublisher.TOPIC_PAYMENT_PROCESSED, orderId.toString(),
                    "{\"orderId\":\"" + orderId + "\",\"status\":\"" + STEP_STATUS + "\"}");
            eventPublisher.publishStatusUpdated(orderId.toString(), currentStatus, STEP_STATUS);
            return SagaStepResult.success(STEP_STATUS);
        } else {
            Instant now = Instant.now();
            orderRepository.updateStatus(orderId, FAILED_STATUS, now);
            orderStatusRepository.save(createHistory(orderId, currentStatus, FAILED_STATUS, "payment-step", now));
            eventPublisher.publish(OrderEventPublisher.TOPIC_PAYMENT_FAILED, orderId.toString(),
                    "{\"orderId\":\"" + orderId + "\",\"reason\":\"validation_failed\"}");
            eventPublisher.publish(OrderEventPublisher.TOPIC_ORDER_CANCELLED, orderId.toString(),
                    "{\"orderId\":\"" + orderId + "\",\"reason\":\"payment_validation_failed\"}");
            log.warn("Compensation: orderId={} PAYMENT_VALIDATION failed, status set to {}", orderId, FAILED_STATUS);
            return SagaStepResult.failure(FAILED_STATUS, true);
        }
    }

    private OrderStatusHistoryEntity createHistory(UUID orderId, String from, String to, String by, Instant at) {
        OrderStatusHistoryEntity h = new OrderStatusHistoryEntity();
        h.setOrderId(orderId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setChangedBy(by);
        h.setChangedAt(at);
        return h;
    }
}
