package com.stefan.knowledgeagent.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RefundApiIntegrationTest extends ApiIntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreatePaymentAndRequestRefundOverHttp() throws Exception {
        // Arrange: create a customer and an order.
        Long customerId = createCustomer();
        long orderId = createOrder(customerId);

        // Register a payment that has already been captured externally.
        String paymentRequest = """
                {
                  "providerReference": "PAY-API-REFUND-%d"
                }
                """.formatted(orderId);

        ResponseEntity<String> paymentResponse = http.postForEntity(
                "/api/v1/orders/" + orderId + "/payment",
                new HttpEntity<>(paymentRequest, jsonHeaders()),
                String.class
        );

        assertEquals(HttpStatus.CREATED, paymentResponse.getStatusCode());
        assertNotNull(paymentResponse.getBody());

        JsonNode paymentJson = objectMapper.readTree(paymentResponse.getBody());
        long paymentId = paymentJson.get("id").asLong();

        assertEquals("CAPTURED", paymentJson.get("status").asText());
        assertEquals(
                0,
                new BigDecimal("100.00")
                        .compareTo(paymentJson.get("amount").decimalValue())
        );

        // Act: request a partial refund.
        String refundRequest = """
                {
                  "amount": 25.00,
                  "reason": "Partial refund requested by customer"
                }
                """;

        HttpHeaders refundHeaders = jsonHeaders();
        refundHeaders.set("Idempotency-Key", "api-refund-key-" + paymentId);

        ResponseEntity<String> refundResponse = http.postForEntity(
                "/api/v1/payments/" + paymentId + "/refunds",
                new HttpEntity<>(refundRequest, refundHeaders),
                String.class
        );

        // Assert: the refund request was accepted.
        assertEquals(HttpStatus.CREATED, refundResponse.getStatusCode());
        assertNotNull(refundResponse.getBody());

        JsonNode refundJson = objectMapper.readTree(refundResponse.getBody());
        long refundId = refundJson.get("id").asLong();

        assertEquals("REQUESTED", refundJson.get("status").asText());
        assertEquals(paymentId, refundJson.get("paymentId").asLong());
        assertEquals(
                0,
                new BigDecimal("25.00")
                        .compareTo(refundJson.get("amount").decimalValue())
        );

        // Retrieve the refund in a separate HTTP request.
        ResponseEntity<String> getResponse = http.getForEntity(
                "/api/v1/refunds/" + refundId,
                String.class
        );

        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertNotNull(getResponse.getBody());

        JsonNode retrieved = objectMapper.readTree(getResponse.getBody());

        assertEquals(refundId, retrieved.get("id").asLong());
        assertEquals("REQUESTED", retrieved.get("status").asText());
    }

    @Test
    void shouldReturnBadRequestWhenIdempotencyKeyIsMissing() throws Exception {
        // Arrange: create a customer, order, and payment.
        Long customerId = createCustomer();
        long orderId = createOrder(customerId);
        long paymentId = createPayment(orderId);

        String refundRequest = """
                {
                  "amount": 10.00,
                  "reason": "Test refund"
                }
                """;

        // Act: submit the refund without an Idempotency-Key header.
        ResponseEntity<String> response = http.exchange(
                "/api/v1/payments/" + paymentId + "/refunds",
                HttpMethod.POST,
                new HttpEntity<>(refundRequest, jsonHeaders()),
                String.class
        );

        // Assert.
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    private long createOrder(Long customerId) throws Exception {
        String request = """
                {
                  "customerId": %d,
                  "currency": "EUR",
                  "items": [
                    {
                      "productId": "SKU-REFUND-002",
                      "productName": "Refund test product",
                      "quantity": 1,
                      "unitPrice": 100.00
                    }
                  ]
                }
                """.formatted(customerId);

        ResponseEntity<String> response = http.postForEntity(
                "/api/v1/orders",
                new HttpEntity<>(request, jsonHeaders()),
                String.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());

        return objectMapper.readTree(response.getBody())
                .get("id")
                .asLong();
    }

    private long createPayment(long orderId) throws Exception {
        String request = """
                {
                  "providerReference": "PAY-REFUND-TEST-%d"
                }
                """.formatted(orderId);

        ResponseEntity<String> response = http.postForEntity(
                "/api/v1/orders/" + orderId + "/payment",
                new HttpEntity<>(request, jsonHeaders()),
                String.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());

        return objectMapper.readTree(response.getBody())
                .get("id")
                .asLong();
    }
}
