package com.platform.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderListResponse(UUID orderId, String customerId, String status, BigDecimal totalAmount, Instant createdAt) {}
