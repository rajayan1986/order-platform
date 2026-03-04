package com.platform.order.dto;

import java.util.List;

public record OrderPageResponse(List<OrderListResponse> content, long totalElements) {}
