package com.platform.order.resilience;

/**
 * Circuit breaker instance name used in application.yml (resilience4j.circuitbreaker.instances.orderService).
 * The CircuitBreaker is obtained from CircuitBreakerRegistry in the controller to avoid bean name clash with OrderService.
 */
public final class CircuitBreakerConfig {

    public static final String ORDER_SERVICE_CIRCUIT_BREAKER = "orderService";

    private CircuitBreakerConfig() {}
}
