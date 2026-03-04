package com.platform.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.order.domain.*;
import com.platform.order.dto.CreateOrderAcceptedResponse;
import com.platform.order.dto.CreateOrderRequest;
import com.platform.order.repository.*;
import com.platform.order.service.OrderService;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import com.platform.order.service.RedisCacheService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private R2dbcEntityTemplate r2dbcEntityTemplate;
    @Mock
    private DatabaseClient databaseClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private org.springframework.r2dbc.core.FetchSpec<Long> fetchSpec;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderStatusHistoryRepository statusHistoryRepository;
    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock
    private TransactionalOperator transactionalOperator;
    @Mock
    private RedisCacheService redisCacheService;

    private OrderService orderService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        orderService = new OrderService(
                orderRepository,
                r2dbcEntityTemplate,
                databaseClient,
                orderItemRepository,
                statusHistoryRepository,
                outboxRepository,
                idempotencyKeyRepository,
                transactionalOperator,
                redisCacheService,
                objectMapper,
                meterRegistry,
                60,
                100
        );
    }

    @Test
    void testCreateOrder_success() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId("cust-1");
        request.setIdempotencyKey(UUID.randomUUID().toString());
        request.setItems(List.of(
                createItemRequest("prod-1", 2, new BigDecimal("10.00"))
        ));

        when(redisCacheService.get(anyString())).thenReturn(Mono.empty());
        when(redisCacheService.incrementAndCheckLimit(anyString(), eq(100), eq(60))).thenReturn(Mono.just(true));
        when(idempotencyKeyRepository.findByKey(anyString())).thenReturn(Mono.empty());
        when(r2dbcEntityTemplate.insert(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(r2dbcEntityTemplate.insert(any(OrderItem.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(r2dbcEntityTemplate.insert(any(OrderStatusHistory.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(databaseClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyInt(), any())).thenReturn(executeSpec);
        when(executeSpec.bindNull(anyInt(), any(Class.class))).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn((org.springframework.r2dbc.core.FetchSpec) fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));
        when(r2dbcEntityTemplate.insert(any(IdempotencyKey.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(redisCacheService.set(anyString(), anyString(), anyLong())).thenReturn(Mono.just(true));
        when(transactionalOperator.transactional(any(reactor.core.publisher.Mono.class))).thenAnswer(inv -> inv.getArgument(0));

        StepVerifier.create(orderService.createOrder(request, "127.0.0.1"))
                .expectNextMatches(r -> r.getOrderId() != null && "PENDING".equals(r.getStatus()))
                .verifyComplete();

        verify(r2dbcEntityTemplate).insert(any(Order.class));
        verify(transactionalOperator).transactional(any(reactor.core.publisher.Mono.class));
    }

    @Test
    void testCreateOrder_duplicateIdempotencyKey_returnsCached() {
        String idempotencyKey = UUID.randomUUID().toString();
        UUID existingOrderId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId("cust-1");
        request.setIdempotencyKey(idempotencyKey);
        request.setItems(List.of(createItemRequest("p1", 1, BigDecimal.ONE)));

        when(redisCacheService.incrementAndCheckLimit(anyString(), eq(100), eq(60))).thenReturn(Mono.just(true));
        when(redisCacheService.get(eq("idempotency:" + idempotencyKey)))
                .thenReturn(Mono.just("{\"orderId\":\"" + existingOrderId + "\",\"status\":\"PENDING\"}"));

        StepVerifier.create(orderService.createOrder(request, "127.0.0.1"))
                .expectNextMatches(r -> existingOrderId.equals(r.getOrderId()))
                .verifyComplete();

        verify(r2dbcEntityTemplate, never()).insert(any(Order.class));
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void testCreateOrder_databaseDown_returns503() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId("cust-1");
        request.setIdempotencyKey(UUID.randomUUID().toString());
        request.setItems(List.of(createItemRequest("p1", 1, BigDecimal.ONE)));

        when(redisCacheService.incrementAndCheckLimit(anyString(), anyInt(), anyInt())).thenReturn(Mono.just(true));
        when(redisCacheService.get(anyString())).thenReturn(Mono.empty());
        when(idempotencyKeyRepository.findByKey(anyString())).thenReturn(Mono.empty());
        when(r2dbcEntityTemplate.insert(any(Order.class))).thenReturn(Mono.error(new RuntimeException("DB down")));
        when(transactionalOperator.transactional(any(reactor.core.publisher.Mono.class))).thenAnswer(inv -> inv.getArgument(0));

        StepVerifier.create(orderService.createOrder(request, "127.0.0.1"))
                .expectError()
                .verify();
    }

    @Test
    void testGetOrder_cacheHit_doesNotHitDatabase() {
        UUID orderId = UUID.randomUUID();
        when(redisCacheService.get(eq("order:" + orderId)))
                .thenReturn(Mono.just("{\"orderId\":\"" + orderId + "\",\"status\":\"PENDING\",\"customerId\":\"c1\",\"totalAmount\":10,\"items\":[],\"statusHistory\":[],\"createdAt\":\"2024-01-01T00:00:00Z\",\"updatedAt\":\"2024-01-01T00:00:00Z\"}"));

        StepVerifier.create(orderService.getOrderById(orderId))
                .expectNextMatches(r -> orderId.equals(r.getOrderId()))
                .verifyComplete();

        verifyNoInteractions(orderRepository);
    }

    @Test
    void testGetOrder_cacheMiss_queriesDatabaseAndPopulatesCache() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, "c1", "PENDING", new BigDecimal("10"), "key", Instant.now(), Instant.now());
        order.setItems(List.of());

        when(redisCacheService.get(eq("order:" + orderId))).thenReturn(Mono.empty());
        when(orderRepository.findById(argThat((UUID id) -> orderId.equals(id)))).thenReturn(Mono.just(order));
        when(statusHistoryRepository.findByOrderIdOrderByChangedAtAsc(orderId)).thenReturn(reactor.core.publisher.Flux.empty());
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(reactor.core.publisher.Flux.empty());
        when(redisCacheService.set(anyString(), anyString(), eq(30L))).thenReturn(Mono.just(true));

        StepVerifier.create(orderService.getOrderById(orderId))
                .expectNextMatches(r -> orderId.equals(r.getOrderId()) && "PENDING".equals(r.getStatus()))
                .verifyComplete();

        verify(redisCacheService).set(eq("order:" + orderId), anyString(), eq(30L));
    }

    @Test
    void testRateLimit_exceededRequests_returns429() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId("cust-1");
        request.setIdempotencyKey(UUID.randomUUID().toString());
        request.setItems(List.of(createItemRequest("p1", 1, BigDecimal.ONE)));

        when(redisCacheService.incrementAndCheckLimit(anyString(), eq(100), eq(60))).thenReturn(Mono.just(false));

        StepVerifier.create(orderService.createOrder(request, "192.168.1.1"))
                .expectError(OrderService.RateLimitExceededException.class)
                .verify();
    }

    private static CreateOrderRequest.OrderItemRequest createItemRequest(String productId, int qty, BigDecimal price) {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(qty);
        item.setPrice(price);
        return item;
    }
}
