package com.platform.order.dto;

import java.util.UUID;

public class CreateOrderAcceptedResponse {

    private UUID orderId;
    private String status = "PENDING";

    public CreateOrderAcceptedResponse() {
    }

    public CreateOrderAcceptedResponse(UUID orderId) {
        this.orderId = orderId;
        this.status = "PENDING";
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
}
