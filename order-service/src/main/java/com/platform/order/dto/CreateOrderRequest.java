package com.platform.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CreateOrderRequest {

    @NotBlank(message = "customerId is required")
    private String customerId;

    @NotNull(message = "items is required")
    @Size(min = 1, message = "At least one item is required")
    @Valid
    private List<OrderItemRequest> items;

    @NotBlank(message = "idempotencyKey is required")
    private String idempotencyKey;

    public CreateOrderRequest() {
    }

    public CreateOrderRequest(String customerId, List<OrderItemRequest> items, String idempotencyKey) {
        this.customerId = customerId;
        this.items = items;
        this.idempotencyKey = idempotencyKey;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public List<OrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public static class OrderItemRequest {
        @NotBlank(message = "productId is required")
        private String productId;

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be positive")
        private Integer quantity;

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.01", message = "price must be positive")
        private java.math.BigDecimal price;

        public OrderItemRequest() {
        }

        public OrderItemRequest(String productId, Integer quantity, java.math.BigDecimal price) {
            this.productId = productId;
            this.quantity = quantity;
            this.price = price;
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

        public java.math.BigDecimal getPrice() {
            return price;
        }

        public void setPrice(java.math.BigDecimal price) {
            this.price = price;
        }
    }
}
