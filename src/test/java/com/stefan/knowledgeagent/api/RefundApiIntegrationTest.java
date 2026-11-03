
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RefundApiIntegrationTest extends ApiIntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateApprovalAndSubmitRefundAfterDifferentApproverDecision() throws Exception {

        long orderId = createOrder(createCustomer());
        long paymentId = createPayment(orderId);

        HttpHeaders requestHeaders = jsonHeaders();
        requestHeaders.set("Idempotency-Key", "api-approval-" + paymentId);

        String approvalRequest = objectMapper.writeValueAsString(Map.of(
                "paymentId", paymentId,
                "amount", new BigDecimal("25.00"),
                "reason", "Partial refund requested by customer"
        ));

        ResponseEntity<String> approvalResponse = http
                .withBasicAuth("agent", "agent-test-password")
                .postForEntity(
                        "/api/v1/approvals/refunds",
                        new HttpEntity<>(approvalRequest, requestHeaders),
                        String.class
                );

        assertEquals(HttpStatus.CREATED, approvalResponse.getStatusCode());
        assertNotNull(approvalResponse.getBody());

        JsonNode approval = objectMapper.readTree(approvalResponse.getBody());
        String approvalId = approval.get("id").asText();

        assertEquals("PENDING", approval.get("status").asText());
        assertEquals("agent", approval.get("requesterSubject").asText());

        ResponseEntity<String> decisionResponse = http
                .withBasicAuth("approver", "approver-test-password")
                .exchange(
                        "/api/v1/approvals/" + approvalId + "/approve",
                        HttpMethod.POST,
                        new HttpEntity<>(jsonHeaders()),
                        String.class
                );

        assertEquals(HttpStatus.OK, decisionResponse.getStatusCode());
        assertNotNull(decisionResponse.getBody());

        JsonNode decision = objectMapper.readTree(decisionResponse.getBody());

        assertEquals("SUBMITTED", decision.get("status").asText());
        assertEquals("approver", decision.get("approverSubject").asText());

        long refundId = decision.get("refundId").asLong();

        ResponseEntity<String> getResponse = http.getForEntity(
                "/api/v1/refunds/" + refundId,
                String.class
        );

        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertNotNull(getResponse.getBody());

        JsonNode refund = objectMapper.readTree(getResponse.getBody());

        assertEquals(refundId, refund.get("id").asLong());
        assertEquals("REQUESTED", refund.get("status").asText());
        assertEquals(paymentId, refund.get("paymentId").asLong());

        assertEquals(
                0,
                new BigDecimal("25.00")
                        .compareTo(refund.get("amount").decimalValue())
        );
    }

    @Test
    void shouldReturnBadRequestWhenApprovalIdempotencyKeyIsMissing() throws Exception {

        long orderId = createOrder(createCustomer());
        long paymentId = createPayment(orderId);

        String request = objectMapper.writeValueAsString(Map.of(
                "paymentId", paymentId,
                "amount", new BigDecimal("10.00"),
                "reason", "Test refund"
        ));

        ResponseEntity<String> response = http
                .withBasicAuth("agent", "agent-test-password")
                .exchange(
                        "/api/v1/approvals/refunds",
                        HttpMethod.POST,
                        new HttpEntity<>(request, jsonHeaders()),
                        String.class
                );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void legacyDirectRefundMutationIsDisabledEvenForApprovers() throws Exception {

        long orderId = createOrder(createCustomer());
        long paymentId = createPayment(orderId);

        String request = """
                {
                  "amount": 10.00,
                  "reason": "Direct mutation bypass test"
                }
                """;

        HttpHeaders headers = jsonHeaders();
        headers.set("Idempotency-Key", "direct-disabled-" + paymentId);

        HttpEntity<String> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> anonymous = http.postForEntity(
                "/api/v1/payments/" + paymentId + "/refunds",
                entity,
                String.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());

        ResponseEntity<String> agent = http
                .withBasicAuth("agent", "agent-test-password")
                .postForEntity(
                        "/api/v1/payments/" + paymentId + "/refunds",
                        entity,
                        String.class
                );

        assertEquals(HttpStatus.FORBIDDEN, agent.getStatusCode());

        ResponseEntity<String> approver = http
                .withBasicAuth("approver", "approver-test-password")
                .postForEntity(
                        "/api/v1/payments/" + paymentId + "/refunds",
                        entity,
                        String.class
                );

        assertEquals(HttpStatus.FORBIDDEN, approver.getStatusCode());
    }

    private long createOrder(Long customerId) {

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

    private long createPayment(long orderId) {

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
