package com.platform.processing.dto;

/**
 * Dashboard statistics for the Processing Service (saga progress, orders processed).
 */
public record ProcessingStatsResponse(
    long ordersProcessedToday,
    long pendingCount,
    long processingCount,
    long shippedCount
) {}
