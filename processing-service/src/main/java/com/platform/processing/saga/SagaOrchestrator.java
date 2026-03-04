package com.platform.processing.saga;

import com.platform.processing.repository.OrderRepository;
import com.platform.processing.steps.ApprovalStep;
import com.platform.processing.steps.ComplianceStep;
import com.platform.processing.steps.PaymentStep;
import com.platform.processing.steps.ShippingStep;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class SagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final PaymentStep paymentStep;
    private final ComplianceStep complianceStep;
    private final ApprovalStep approvalStep;
    private final ShippingStep shippingStep;
    private final OrderRepository orderRepository;
    private final MeterRegistry meterRegistry;
    private final Timer sagaStepDuration;

    public SagaOrchestrator(PaymentStep paymentStep,
                            ComplianceStep complianceStep,
                            ApprovalStep approvalStep,
                            ShippingStep shippingStep,
                            OrderRepository orderRepository,
                            MeterRegistry meterRegistry) {
        this.paymentStep = paymentStep;
        this.complianceStep = complianceStep;
        this.approvalStep = approvalStep;
        this.shippingStep = shippingStep;
        this.orderRepository = orderRepository;
        this.meterRegistry = meterRegistry;
        this.sagaStepDuration = meterRegistry.timer("saga.step.duration", "step", "all");
    }

    public String processOrder(UUID orderId) {
        Timer.Sample sample = Timer.start();
        try {
            Optional<com.platform.processing.entity.OrderEntity> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                log.error("Order not found: {}", orderId);
                return null;
            }
            String currentStatus = orderOpt.get().getStatus();
            log.info("Starting saga for orderId={}, currentStatus={}", orderId, currentStatus);

            SagaStepResult paymentResult = paymentStep.execute(orderId, currentStatus);
            if (!paymentResult.success()) {
                log.info("Saga stopped: orderId={} payment failed, finalStatus={}", orderId, paymentResult.newStatus());
                recordProcessed(paymentResult.newStatus());
                return paymentResult.newStatus();
            }
            currentStatus = paymentResult.newStatus();

            SagaStepResult complianceResult = complianceStep.execute(orderId, currentStatus);
            if (!complianceResult.success()) {
                recordProcessed(complianceResult.newStatus());
                return complianceResult.newStatus();
            }
            currentStatus = complianceResult.newStatus();

            SagaStepResult approvalResult = approvalStep.execute(orderId, currentStatus);
            if (!approvalResult.success()) {
                log.info("Saga stopped: orderId={} approval rejected, finalStatus={}", orderId, approvalResult.newStatus());
                recordProcessed(approvalResult.newStatus());
                return approvalResult.newStatus();
            }
            currentStatus = approvalResult.newStatus();

            SagaStepResult shippingResult = shippingStep.execute(orderId, currentStatus);
            log.info("Saga completed for orderId={}, finalStatus={}", orderId, shippingResult.newStatus());
            recordProcessed(shippingResult.newStatus());
            return shippingResult.newStatus();
        } finally {
            sample.stop(sagaStepDuration);
        }
    }

    private void recordProcessed(String finalStatus) {
        meterRegistry.counter("orders.processed.total", "final_status", finalStatus != null ? finalStatus : "unknown").increment();
    }
}
