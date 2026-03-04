package com.platform.order.dto;

import java.util.Map;

/**
 * Dashboard statistics for KPI cards and pipeline stages.
 */
public record DashboardStatsResponse(
        long totalOrders,
        long ordersToday,
        long pendingCount,
        long processingCount,
        long shippedCount,
        Map<String, Long> byStatus
) {}
