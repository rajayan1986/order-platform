package com.platform.order.controller;

import com.platform.order.dto.CreateOrderAcceptedResponse;
import com.platform.order.dto.CreateOrderRequest;
import com.platform.order.dto.DashboardStatsResponse;
import com.platform.order.dto.OrderPageResponse;
import com.platform.order.dto.OrderResponse;
import com.platform.order.resilience.CircuitBreakerConfig;
import com.platform.order.service.OrderService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final CircuitBreaker circuitBreaker;

    public OrderController(OrderService orderService,
                           CircuitBreakerRegistry circuitBreakerRegistry) {
        this.orderService = orderService;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CircuitBreakerConfig.ORDER_SERVICE_CIRCUIT_BREAKER);
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public Mono<ResponseEntity<?>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        String clientIp = forwardedFor != null ? forwardedFor.split(",")[0].trim() : "127.0.0.1";
        return orderService.createOrder(request, clientIp)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .<ResponseEntity<?>>map(response -> ResponseEntity.status(HttpStatus.ACCEPTED).body(response))
                .onErrorResume(OrderService.RateLimitExceededException.class, e ->
                        Mono.just((ResponseEntity<?>) ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                .header("Retry-After", "60")
                                .build()))
                .onErrorResume(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class, e -> {
                    log.warn("Circuit breaker open, returning 503");
                    return Mono.just((ResponseEntity<?>) ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .header("Retry-After", String.valueOf(Duration.ofSeconds(10).getSeconds()))
                            .build());
                });
    }

    @GetMapping("/stats")
    public Mono<ResponseEntity<DashboardStatsResponse>> getDashboardStats() {
        return orderService.getDashboardStats()
                .map(ResponseEntity::ok)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()));
    }

    @GetMapping
    public Mono<ResponseEntity<OrderPageResponse>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return orderService.listOrders(page, size)
                .zipWith(orderService.countOrders())
                .map(tuple -> new OrderPageResponse(tuple.getT1(), tuple.getT2()))
                .map(ResponseEntity::ok)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<?>> getOrder(@PathVariable UUID id) {
        return orderService.getOrderById(id)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .onErrorResume(OrderService.OrderNotFoundException.class, e ->
                        Mono.just((ResponseEntity<?>) ResponseEntity.notFound().build()))
                .onErrorResume(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class, e ->
                        Mono.just((ResponseEntity<?>) ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .header("Retry-After", "10")
                                .build()));
    }
}
