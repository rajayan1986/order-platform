package com.platform.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrderResponse {

    private UUID orderId;
    private String status;
    private List<StatusHistoryEntry> statusHistory;
    private String customerId;
    private BigDecimal totalAmount;
    private List<OrderItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;

    public OrderResponse() {
    }

    public OrderResponse(UUID orderId, String status, List<StatusHistoryEntry> statusHistory,
                         String customerId, BigDecimal totalAmount, List<OrderItemResponse> items,
                         Instant createdAt, Instant updatedAt) {
        this.orderId = orderId;
        this.status = status;
        this.statusHistory = statusHistory;
        this.customerId = customerId;
        this.totalAmount = totalAmount;
        this.items = items;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<StatusHistoryEntry> getStatusHistory() {
        return statusHistory;
    }

    public void setStatusHistory(List<StatusHistoryEntry> statusHistory) {
        this.statusHistory = statusHistory;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public List<OrderItemResponse> getItems() {
        return items;
    }

    public void setItems(List<OrderItemResponse> items) {
        this.items = items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static class OrderItemResponse {
        private UUID id;
        private String productId;
        private Integer quantity;
        private BigDecimal unitPrice;

        public OrderItemResponse() {
        }

        public OrderItemResponse(UUID id, String productId, Integer quantity, BigDecimal unitPrice) {
            this.id = id;
            this.productId = productId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getProductId() {
            return productId;
        }

        public void setProductId(String productId) {
            this.productId = productId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }
    }

    public static class StatusHistoryEntry {
        private String fromStatus;
        private String toStatus;
        private Instant changedAt;

        public StatusHistoryEntry() {
        }

        public StatusHistoryEntry(String fromStatus, String toStatus, Instant changedAt) {
            this.fromStatus = fromStatus;
            this.toStatus = toStatus;
            this.changedAt = changedAt;
        }

        public String getFromStatus() {
            return fromStatus;
        }

        public void setFromStatus(String fromStatus) {
            this.fromStatus = fromStatus;
        }

        public String getToStatus() {
            return toStatus;
        }

        public void setToStatus(String toStatus) {
            this.toStatus = toStatus;
        }

        public Instant getChangedAt() {
            return changedAt;
        }

        public void setChangedAt(Instant changedAt) {
            this.changedAt = changedAt;
        }
    }
}
