package com.platform.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.order.domain.*;
import com.platform.order.dto.CreateOrderAcceptedResponse;
import com.platform.order.dto.CreateOrderRequest;
import com.platform.order.dto.OrderListResponse;
import com.platform.order.dto.OrderResponse;
import com.platform.order.repository.*;
import io.micrometer.core.instrument.Counter;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.platform.order.dto.DashboardStatsResponse;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final String CACHE_KEY_PREFIX = "order:";
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:";
    private static final String STATUS_PENDING = "PENDING";
    private static final int CACHE_TTL_SECONDS = 30;
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final DatabaseClient databaseClient;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final OutboxRepository outboxRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final TransactionalOperator transactionalOperator;
    private final RedisCacheService redisCacheService;
    private final ObjectMapper objectMapper;
    private final int idempotencyTtlSeconds;
    private final int rateLimitRequestsPerMinute;
    private final Counter ordersCreatedTotal;
    private final Timer orderCreationLatency;
    private final Counter cacheHitsTotal;
    private final Counter cacheMissesTotal;
    private final Counter rateLimitRejectedTotal;

    public OrderService(OrderRepository orderRepository,
                        R2dbcEntityTemplate r2dbcEntityTemplate,
                        DatabaseClient databaseClient,
                        OrderItemRepository orderItemRepository,
                        OrderStatusHistoryRepository statusHistoryRepository,
                        OutboxRepository outboxRepository,
                        IdempotencyKeyRepository idempotencyKeyRepository,
                        TransactionalOperator transactionalOperator,
                        RedisCacheService redisCacheService,
                        ObjectMapper objectMapper,
                        MeterRegistry meterRegistry,
                        @Value("${app.idempotency.ttl-seconds:60}") int idempotencyTtlSeconds,
                        @Value("${app.rate-limit.requests-per-minute:100}") int rateLimitRequestsPerMinute) {
        this.orderRepository = orderRepository;
        this.r2dbcEntityTemplate = r2dbcEntityTemplate;
        this.databaseClient = databaseClient;
        this.orderItemRepository = orderItemRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.outboxRepository = outboxRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.transactionalOperator = transactionalOperator;
        this.redisCacheService = redisCacheService;
        this.objectMapper = objectMapper;
        this.idempotencyTtlSeconds = idempotencyTtlSeconds;
        this.rateLimitRequestsPerMinute = rateLimitRequestsPerMinute;
        this.ordersCreatedTotal = meterRegistry.counter("orders.created.total", "status", "accepted");
        this.orderCreationLatency = meterRegistry.timer("orders.creation.latency");
        this.cacheHitsTotal = meterRegistry.counter("cache.hits.total");
        this.cacheMissesTotal = meterRegistry.counter("cache.misses.total");
        this.rateLimitRejectedTotal = meterRegistry.counter("rate.limit.rejected.total");
    }

    public Mono<CreateOrderAcceptedResponse> createOrder(CreateOrderRequest request, String clientIp) {
        String idempotencyKey = request.getIdempotencyKey();
        return checkRateLimit(clientIp)
                .flatMap(allowed -> {
                    if (!allowed) {
                        rateLimitRejectedTotal.increment();
                        return Mono.error(new RateLimitExceededException());
                    }
                    return checkRedisIdempotency(idempotencyKey)
                            .switchIfEmpty(Mono.defer(() ->
                                    checkDbIdempotency(idempotencyKey)
                                            .doOnNext(existing -> log.warn("Idempotency hit (DB) for key: {}", idempotencyKey))
                                            .flatMap(existing -> Mono.just(new CreateOrderAcceptedResponse(existing.getOrderId())))
                                            .switchIfEmpty(Mono.defer(() -> persistOrderAndOutbox(request)))
                            ));
                })
                .doOnNext(r -> log.info("Order created: orderId={}", r.getOrderId()));
    }

    private Mono<Boolean> checkRateLimit(String clientIp) {
        String key = RATE_LIMIT_KEY_PREFIX + clientIp;
        return redisCacheService.incrementAndCheckLimit(key, rateLimitRequestsPerMinute, 60)
                .map(withinLimit -> withinLimit);
    }

    private Mono<CreateOrderAcceptedResponse> checkRedisIdempotency(String idempotencyKey) {
        return redisCacheService.get(IDEMPOTENCY_KEY_PREFIX + idempotencyKey)
                .flatMap(json -> {
                    try {
                        CreateOrderAcceptedResponse response = objectMapper.readValue(json, CreateOrderAcceptedResponse.class);
                        return Mono.just(response);
                    } catch (JsonProcessingException e) {
                        return Mono.empty();
                    }
                })
                .doOnNext(r -> log.warn("Idempotency hit (Redis) for key: {}", idempotencyKey))
                .onErrorResume(e -> Mono.empty());
    }

    private Mono<IdempotencyKey> checkDbIdempotency(String key) {
        return idempotencyKeyRepository.findByKey(key)
                .onErrorResume(e -> Mono.empty());
    }

    /** Insert outbox event using raw SQL so payload (String) is cast to JSONB; avoids R2DBC entity insert codec issue. */
    private Mono<Void> insertOutboxEvent(OutboxEvent outbox) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(
                "INSERT INTO outbox_events (id, aggregate_id, aggregate_type, event_type, payload, published_at, created_at) "
                        + "VALUES ($1, $2, $3, $4, $5::jsonb, $6, $7)")
                .bind(0, outbox.getId())
                .bind(1, outbox.getAggregateId())
                .bind(2, outbox.getAggregateType())
                .bind(3, outbox.getEventType())
                .bind(4, outbox.getPayload());
        if (outbox.getPublishedAt() != null) {
            spec = spec.bind(5, outbox.getPublishedAt());
        } else {
            spec = spec.bindNull(5, Instant.class);
        }
        return spec.bind(6, outbox.getCreatedAt())
                .fetch().rowsUpdated().then();
    }

    private Mono<CreateOrderAcceptedResponse> persistOrderAndOutbox(CreateOrderRequest request) {
        BigDecimal totalAmount = request.getItems().stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant now = Instant.now();
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, request.getCustomerId(), STATUS_PENDING, totalAmount,
                request.getIdempotencyKey(), now, now);
        List<OrderItem> items = request.getItems().stream()
                .map(i -> new OrderItem(UUID.randomUUID(), orderId, i.getProductId(), i.getQuantity(), i.getPrice()))
                .collect(Collectors.toList());
        OrderStatusHistory initialHistory = new OrderStatusHistory(UUID.randomUUID(), orderId, null, STATUS_PENDING, "system", now);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(new OrderCreatedPayload(orderId, request.getCustomerId(), request.getItems(), totalAmount, now));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
        OutboxEvent outbox = new OutboxEvent(UUID.randomUUID(), orderId, "Order", "order.created", payload, null, now);
        IdempotencyKey idempKey = new IdempotencyKey(request.getIdempotencyKey(), orderId, 202, now);

        // Use insert() for new entities so we always INSERT; save() would do UPDATE when entity has non-null id
        Mono<CreateOrderAcceptedResponse> saveChain = r2dbcEntityTemplate.insert(order)
                .thenMany(reactor.core.publisher.Flux.fromIterable(items)).flatMap(r2dbcEntityTemplate::insert).then()
                .then(r2dbcEntityTemplate.insert(initialHistory))
                .then(insertOutboxEvent(outbox))
                .then(r2dbcEntityTemplate.insert(idempKey))
                .thenReturn(new CreateOrderAcceptedResponse(orderId));

        return transactionalOperator.transactional(saveChain)
                .flatMap(response -> {
                    ordersCreatedTotal.increment();
                    try {
                        String json = objectMapper.writeValueAsString(response);
                        return redisCacheService.set(IDEMPOTENCY_KEY_PREFIX + request.getIdempotencyKey(), json, idempotencyTtlSeconds)
                                .thenReturn(response);
                    } catch (JsonProcessingException e) {
                        return Mono.just(response);
                    }
                });
    }

    public Mono<List<OrderListResponse>> listOrders(int page, int size) {
        return orderRepository.findAllByOrderByCreatedAtDesc()
                .skip((long) page * size)
                .take(size)
                .map(o -> new OrderListResponse(o.getId(), o.getCustomerId(), o.getStatus(), o.getTotalAmount(), o.getCreatedAt()))
                .collectList();
    }

    public Mono<Long> countOrders() {
        return orderRepository.count();
    }

    private static final String[] STATUSES_FOR_STATS = {
            "PENDING", "PAYMENT_VALIDATED", "COMPLIANCE_CHECKED", "APPROVED", "SHIPPED",
            "PAYMENT_FAILED", "REJECTED", "CANCELLED"
    };

    public Mono<DashboardStatsResponse> getDashboardStats() {
        Instant startOfToday = Instant.now().atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Mono<Long> totalM = orderRepository.count();
        Mono<Long> todayM = orderRepository.countByCreatedAtGreaterThanEqual(startOfToday);
        Mono<Long> pendingM = orderRepository.countByStatus("PENDING");
        Mono<Long> shippedM = orderRepository.countByStatus("SHIPPED");
        Mono<Map<String, Long>> byStatusM = Flux.fromArray(STATUSES_FOR_STATS)
                .flatMap(s -> orderRepository.countByStatus(s).map(c -> Map.entry(s, c)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(m -> new LinkedHashMap<>(m));
        Mono<Long> processingM = byStatusM.map(m ->
                (m.getOrDefault("PAYMENT_VALIDATED", 0L)) + (m.getOrDefault("COMPLIANCE_CHECKED", 0L)) + (m.getOrDefault("APPROVED", 0L)));

        return Mono.zip(totalM, todayM, pendingM, processingM, shippedM, byStatusM)
                .map(t -> new DashboardStatsResponse(t.getT1(), t.getT2(), t.getT3(), t.getT4(), t.getT5(), t.getT6()));
    }

    public Mono<OrderResponse> getOrderById(UUID id) {
        String cacheKey = CACHE_KEY_PREFIX + id;
        return redisCacheService.get(cacheKey)
                .flatMap(json -> {
                    try {
                        cacheHitsTotal.increment();
                        OrderResponse response = objectMapper.readValue(json, OrderResponse.class);
                        return Mono.just(response);
                    } catch (JsonProcessingException e) {
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(Mono.defer(() -> loadOrderFromDb(id, cacheKey)));
    }

    private Mono<OrderResponse> loadOrderFromDb(UUID id, String cacheKey) {
        cacheMissesTotal.increment();
        return orderRepository.findById(id)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(id)))
                .flatMap(order -> statusHistoryRepository.findByOrderIdOrderByChangedAtAsc(id)
                        .collectList()
                        .zipWith(orderItemRepository.findByOrderId(id).collectList())
                        .map(tuple -> {
                            List<OrderStatusHistory> history = tuple.getT1();
                            List<OrderItem> items = tuple.getT2();
                            order.setItems(items);
                            OrderResponse response = toResponse(order, history);
                            return response;
                        })
                        .flatMap(response -> {
                            try {
                                String json = objectMapper.writeValueAsString(response);
                                return redisCacheService.set(cacheKey, json, CACHE_TTL_SECONDS)
                                        .thenReturn(response);
                            } catch (JsonProcessingException e) {
                                return Mono.just(response);
                            }
                        }));
    }

    private OrderResponse toResponse(Order order, List<OrderStatusHistory> history) {
        List<OrderResponse.StatusHistoryEntry> historyEntries = history.stream()
                .map(h -> new OrderResponse.StatusHistoryEntry(h.getFromStatus(), h.getToStatus(), h.getChangedAt()))
                .collect(Collectors.toList());
        List<OrderResponse.OrderItemResponse> itemResponses = order.getItems().stream()
                .map(i -> new OrderResponse.OrderItemResponse(i.getId(), i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .collect(Collectors.toList());
        return new OrderResponse(order.getId(), order.getStatus(), historyEntries, order.getCustomerId(),
                order.getTotalAmount(), itemResponses, order.getCreatedAt(), order.getUpdatedAt());
    }

    public static class OrderCreatedPayload {
        public UUID orderId;
        public String customerId;
        public List<CreateOrderRequest.OrderItemRequest> items;
        public BigDecimal totalAmount;
        public Instant createdAt;

        public OrderCreatedPayload(UUID orderId, String customerId, List<CreateOrderRequest.OrderItemRequest> items, BigDecimal totalAmount, Instant createdAt) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.items = items;
            this.totalAmount = totalAmount;
            this.createdAt = createdAt;
        }
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException() {
            super("Rate limit exceeded");
        }
    }

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(UUID id) {
            super("Order not found: " + id);
        }
    }
}
