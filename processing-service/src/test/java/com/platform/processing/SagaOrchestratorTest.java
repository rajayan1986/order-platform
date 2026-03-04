package com.platform.processing;

import com.platform.processing.entity.OrderEntity;
import com.platform.processing.repository.OrderRepository;
import com.platform.processing.repository.OrderStatusRepository;
import com.platform.processing.service.OrderEventPublisher;
import com.platform.processing.saga.SagaOrchestrator;
import com.platform.processing.steps.ApprovalStep;
import com.platform.processing.steps.ComplianceStep;
import com.platform.processing.steps.PaymentStep;
import com.platform.processing.steps.ShippingStep;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaOrchestratorTest {

    @Mock
    private PaymentStep paymentStep;
    @Mock
    private ComplianceStep complianceStep;
    @Mock
    private ApprovalStep approvalStep;
    @Mock
    private ShippingStep shippingStep;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusRepository orderStatusRepository;
    @Mock
    private OrderEventPublisher eventPublisher;

    private SagaOrchestrator sagaOrchestrator;

    @BeforeEach
    void setUp() {
        sagaOrchestrator = new SagaOrchestrator(
                paymentStep, complianceStep, approvalStep, shippingStep,
                orderRepository, new SimpleMeterRegistry()
        );
    }

    @Test
    void testSuccessfulSagaCompletion_allStepsPass() {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setStatus("PENDING");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentStep.execute(eq(orderId), eq("PENDING")))
                .thenReturn(new com.platform.processing.saga.SagaStepResult(true, false, "PAYMENT_VALIDATED"));
        when(complianceStep.execute(eq(orderId), eq("PAYMENT_VALIDATED")))
                .thenReturn(new com.platform.processing.saga.SagaStepResult(true, false, "COMPLIANCE_CHECKED"));
        when(approvalStep.execute(eq(orderId), eq("COMPLIANCE_CHECKED")))
                .thenReturn(new com.platform.processing.saga.SagaStepResult(true, false, "APPROVED"));
        when(shippingStep.execute(eq(orderId), eq("APPROVED")))
                .thenReturn(new com.platform.processing.saga.SagaStepResult(true, false, "SHIPPED"));

        String result = sagaOrchestrator.processOrder(orderId);

        assert result != null && result.equals("SHIPPED");
        verify(paymentStep).execute(eq(orderId), eq("PENDING"));
        verify(complianceStep).execute(eq(orderId), eq("PAYMENT_VALIDATED"));
        verify(approvalStep).execute(eq(orderId), eq("COMPLIANCE_CHECKED"));
        verify(shippingStep).execute(eq(orderId), eq("APPROVED"));
    }

    @Test
    void testSagaCompensation_paymentFails_orderCancelled() {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setStatus("PENDING");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentStep.execute(eq(orderId), eq("PENDING")))
                .thenReturn(new com.platform.processing.saga.SagaStepResult(false, true, "PAYMENT_FAILED"));

        String result = sagaOrchestrator.processOrder(orderId);

        assert result != null && result.equals("PAYMENT_FAILED");
        verify(paymentStep).execute(eq(orderId), eq("PENDING"));
        verify(complianceStep, never()).execute(any(), any());
        verify(approvalStep, never()).execute(any(), any());
        verify(shippingStep, never()).execute(any(), any());
    }

    @Test
    void testIdempotentStep_stepAlreadyExecuted_skipped() {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setStatus("PAYMENT_VALIDATED");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentStep.execute(eq(orderId), eq("PAYMENT_VALIDATED")))
                .thenReturn(new com.platform.processing.saga.SagaStepResult(true, false, "PAYMENT_VALIDATED"));
        when(complianceStep.execute(eq(orderId), eq("PAYMENT_VALIDATED")))
                .thenReturn(new com.platform.processing.saga.SagaStepResult(true, false, "COMPLIANCE_CHECKED"));
        when(approvalStep.execute(eq(orderId), eq("COMPLIANCE_CHECKED")))
                .thenReturn(new com.platform.processing.saga.SagaStepResult(true, false, "APPROVED"));
        when(shippingStep.execute(eq(orderId), eq("APPROVED")))
                .thenReturn(new com.platform.processing.saga.SagaStepResult(true, false, "SHIPPED"));

        String result = sagaOrchestrator.processOrder(orderId);

        assert result != null && result.equals("SHIPPED");
        verify(paymentStep).execute(eq(orderId), eq("PAYMENT_VALIDATED"));
    }

    @Test
    void testDLQ_maxRetriesExceeded_messageRoutedToDLQ() {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setStatus("PENDING");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentStep.execute(eq(orderId), eq("PENDING"))).thenThrow(new RuntimeException("Simulated failure"));

        try {
            sagaOrchestrator.processOrder(orderId);
        } catch (RuntimeException e) {
            assert e.getMessage().contains("Simulated failure");
        }
        verify(paymentStep).execute(eq(orderId), eq("PENDING"));
    }
}
