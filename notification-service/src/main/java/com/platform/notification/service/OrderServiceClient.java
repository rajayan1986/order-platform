package com.platform.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class OrderServiceClient {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceClient.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String orderServiceUrl;

    public OrderServiceClient(@Value("${app.order-service.url:http://localhost:8087}") String orderServiceUrl) {
        this.orderServiceUrl = orderServiceUrl.endsWith("/") ? orderServiceUrl : orderServiceUrl + "/";
    }

    public String getOrderStatus(String orderId) {
        try {
            String url = orderServiceUrl + "orders/" + orderId;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode node = objectMapper.readTree(response.getBody());
                return node.has("status") ? node.get("status").asText() : null;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch order status for orderId={}: {}", orderId, e.getMessage());
        }
        return null;
    }
}
