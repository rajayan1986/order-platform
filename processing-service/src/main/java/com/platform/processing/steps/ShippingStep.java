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
public class ShippingStep {

    private static final Logger log = LoggerFactory.getLogger(ShippingStep.class);
    private static final String STEP_STATUS = "SHIPPED";

    private final OrderRepository orderRepository;
    private final OrderStatusRepository orderStatusRepository;
    private final OrderEventPublisher eventPublisher;

    public ShippingStep(OrderRepository orderRepository,
                         OrderStatusRepository orderStatusRepository,
                         OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderStatusRepository = orderStatusRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SagaStepResult execute(UUID orderId, String currentStatus) {
        if (orderStatusRepository.existsByOrderIdAndToStatus(orderId, STEP_STATUS)) {
            log.warn("Step SHIPPING already executed for orderId={}, skipping", orderId);
            return SagaStepResult.success(STEP_STATUS);
        }
        Instant now = Instant.now();
        orderRepository.updateStatus(orderId, STEP_STATUS, now);
        orderStatusRepository.save(createHistory(orderId, currentStatus, STEP_STATUS, "shipping-step", now));
        eventPublisher.publish(OrderEventPublisher.TOPIC_ORDER_SHIPPED, orderId.toString(),
                "{\"orderId\":\"" + orderId + "\",\"status\":\"" + STEP_STATUS + "\"}");
        eventPublisher.publishStatusUpdated(orderId.toString(), currentStatus, STEP_STATUS);
        return SagaStepResult.success(STEP_STATUS);
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
