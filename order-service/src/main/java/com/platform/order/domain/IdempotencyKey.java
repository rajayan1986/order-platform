package com.platform.order.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("idempotency_keys")
public class IdempotencyKey {

    @Id
    @Column("key")
    private String key;
    @Column("order_id")
    private UUID orderId;
    @Column("response_code")
    private Integer responseCode;
    @Column("created_at")
    private Instant createdAt;

    public IdempotencyKey() {
    }

    public IdempotencyKey(String key, UUID orderId, Integer responseCode, Instant createdAt) {
        this.key = key;
        this.orderId = orderId;
        this.responseCode = responseCode;
        this.createdAt = createdAt;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(Integer responseCode) {
        this.responseCode = responseCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
