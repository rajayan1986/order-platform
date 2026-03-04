package com.platform.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class OrderControllerIntegrationTest {

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("orderdb")
            .withUsername("orderuser")
            .withPassword("orderpass")
            .withInitScript("init.sql");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
                "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getFirstMappedPort().toString());
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    @Autowired
    private WebTestClient webTestClient;

    private String getAuthToken() {
        String tokenBody = "{\"username\":\"admin\",\"password\":\"admin123\"}";
        Map<?, ?> response = webTestClient.post().uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(tokenBody)
                .exchange()
                .expectStatus().isOk()
                .returnResult(Map.class)
                .getResponseBody()
                .blockFirst();
        return "Bearer " + (response != null ? response.get("token") : "");
    }

    @Test
    void testFullOrderCreationFlow() {
        String token = getAuthToken();
        String idempotencyKey = UUID.randomUUID().toString();
        String body = """
                {
                  "customerId": "cust-int-1",
                  "idempotencyKey": "%s",
                  "items": [
                    {"productId": "prod-1", "quantity": 2, "price": 15.50}
                  ]
                }
                """.formatted(idempotencyKey);

        webTestClient.post().uri("/orders")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.orderId").exists()
                .jsonPath("$.status").isEqualTo("PENDING");
    }

    @Test
    void testIdempotencyEndToEnd() {
        String token = getAuthToken();
        String idempotencyKey = UUID.randomUUID().toString();
        String body = """
                {
                  "customerId": "cust-2",
                  "idempotencyKey": "%s",
                  "items": [{"productId": "p1", "quantity": 1, "price": 10}]
                }
                """.formatted(idempotencyKey);

        var response1 = webTestClient.post().uri("/orders")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isAccepted()
                .returnResult(Map.class)
                .getResponseBody()
                .blockFirst();

        webTestClient.post().uri("/orders")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.orderId").isEqualTo(response1 != null ? response1.get("orderId").toString() : null)
                .jsonPath("$.status").isEqualTo("PENDING");
    }
}
