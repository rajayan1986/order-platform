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
public class ApprovalStep {

    private static final Logger log = LoggerFactory.getLogger(ApprovalStep.class);
    private static final String APPROVED_STATUS = "APPROVED";
    private static final String REJECTED_STATUS = "REJECTED";

    private final OrderRepository orderRepository;
    private final OrderStatusRepository orderStatusRepository;
    private final OrderEventPublisher eventPublisher;

    public ApprovalStep(OrderRepository orderRepository,
                        OrderStatusRepository orderStatusRepository,
                        OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderStatusRepository = orderStatusRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SagaStepResult execute(UUID orderId, String currentStatus) {
        if (orderStatusRepository.existsByOrderIdAndToStatus(orderId, APPROVED_STATUS)) {
            log.warn("Step APPROVAL already executed for orderId={}, skipping", orderId);
            return SagaStepResult.success(APPROVED_STATUS);
        }
        if (orderStatusRepository.existsByOrderIdAndToStatus(orderId, REJECTED_STATUS)) {
            log.warn("Step APPROVAL already rejected for orderId={}, skipping", orderId);
            return SagaStepResult.failure(REJECTED_STATUS, true);
        }
        boolean approved = Math.random() < 0.95;
        Instant now = Instant.now();
        if (approved) {
            orderRepository.updateStatus(orderId, APPROVED_STATUS, now);
            orderStatusRepository.save(createHistory(orderId, currentStatus, APPROVED_STATUS, "approval-step", now));
            eventPublisher.publish(OrderEventPublisher.TOPIC_ORDER_APPROVED, orderId.toString(),
                    "{\"orderId\":\"" + orderId + "\",\"status\":\"" + APPROVED_STATUS + "\"}");
            eventPublisher.publishStatusUpdated(orderId.toString(), currentStatus, APPROVED_STATUS);
            return SagaStepResult.success(APPROVED_STATUS);
        } else {
            orderRepository.updateStatus(orderId, REJECTED_STATUS, now);
            orderStatusRepository.save(createHistory(orderId, currentStatus, REJECTED_STATUS, "approval-step", now));
            eventPublisher.publish(OrderEventPublisher.TOPIC_ORDER_CANCELLED, orderId.toString(),
                    "{\"orderId\":\"" + orderId + "\",\"reason\":\"approval_rejected\"}");
            eventPublisher.publishStatusUpdated(orderId.toString(), currentStatus, REJECTED_STATUS);
            log.warn("Compensation: orderId={} APPROVAL rejected, status set to {}", orderId, REJECTED_STATUS);
            return SagaStepResult.failure(REJECTED_STATUS, true);
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
