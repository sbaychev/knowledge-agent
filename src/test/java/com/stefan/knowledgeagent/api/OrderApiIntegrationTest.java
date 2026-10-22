package com.stefan.knowledgeagent.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderApiIntegrationTest extends ApiIntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateAndRetrieveOrderOverHttp() throws Exception {
        // Arrange
        Long customerId = createCustomer();

        String request = """
                {
                  "customerId": %d,
                  "currency": "EUR",
                  "items": [
                    {
                      "productId": "SKU-API-001",
                      "productName": "API test product",
                      "quantity": 2,
                      "unitPrice": 49.50
                    }
                  ]
                }
                """.formatted(customerId);

        // Act: create the order over HTTP.
        ResponseEntity<String> createResponse = http.postForEntity(
                "/api/v1/orders",
                new HttpEntity<>(request, jsonHeaders()),
                String.class
        );

        // Assert: creation response.
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        assertNotNull(createResponse.getBody());

        JsonNode created = objectMapper.readTree(createResponse.getBody());

        long orderId = created.get("id").asLong();

        assertEquals(customerId, created.get("customerId").asLong());
        assertEquals("CREATED", created.get("status").asText());
        assertEquals(
                0,
                new BigDecimal("99.0000")
                        .compareTo(created.get("totalAmount").decimalValue())
        );
        assertEquals("EUR", created.get("currency").asText());
        assertEquals(1, created.get("items").size());
        assertEquals(
                2,
                created.get("items").get(0).get("quantity").asInt()
        );

        // Act: retrieve the order in a separate HTTP request.
        ResponseEntity<String> getResponse = http.getForEntity(
                "/api/v1/orders/" + orderId,
                String.class
        );

        // Assert: retrieval response.
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertNotNull(getResponse.getBody());

        JsonNode retrieved = objectMapper.readTree(getResponse.getBody());

        assertEquals(orderId, retrieved.get("id").asLong());
        assertEquals(
                "SKU-API-001",
                retrieved.get("items").get(0).get("productId").asText()
        );
    }

    @Test
    void shouldReturnBadRequestForInvalidOrderRequest() {
        String request = """
                {
                  "customerId": -1,
                  "currency": "EURO",
                  "items": []
                }
                """;

        ResponseEntity<String> response = http.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("VALIDATION_FAILED"));
    }

    @Test
    void shouldReturnNotFoundForUnknownOrder() {
        ResponseEntity<String> response = http.getForEntity(
                "/api/v1/orders/999999999",
                String.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("RESOURCE_NOT_FOUND"));
    }
}
