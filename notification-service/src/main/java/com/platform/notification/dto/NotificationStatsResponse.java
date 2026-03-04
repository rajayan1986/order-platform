package com.platform.notification.dto;

/**
 * Dashboard statistics for the Notification Service (live connections, events delivered).
 */
public record NotificationStatsResponse(
    int connectedWebSockets,
    int connectedSse,
    long eventsDeliveredTotal
) {}
