
package com.stefan.knowledgeagent.approval;

import com.stefan.knowledgeagent.api.ApiIntegrationTestBase;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestPropertySource(properties = "approval.refund.expiration=PT0.001S")
class RefundApprovalExpiryIntegrationTest extends ApiIntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void expiresPendingApprovalWhenItIsReadAfterItsDeadline() {

        long paymentId = createPayment();

        HttpHeaders headers = jsonHeaders();
        headers.set("Idempotency-Key", "expiry-" + UUID.randomUUID());

        String body = objectMapper.writeValueAsString(Map.of(
                "paymentId", paymentId,
                "amount", new BigDecimal("10.00"),
                "reason", "Expire this approval in the integration test"
        ));

        ResponseEntity<String> created = http
                .withBasicAuth("agent", "agent-test-password")
                .postForEntity(
                        "/api/v1/approvals/refunds",
                        new HttpEntity<>(body, headers),
                        String.class
                );

        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        UUID approvalId = UUID.fromString(
                objectMapper.readTree(created.getBody()).get("id").asString()
        );

        ResponseEntity<String> read = http
                .withBasicAuth("agent", "agent-test-password")
                .getForEntity(
                        "/api/v1/approvals/" + approvalId,
                        String.class
                );

        assertEquals(HttpStatus.OK, read.getStatusCode());

        JsonNode approval = objectMapper.readTree(read.getBody());

        assertEquals("EXPIRED", approval.get("status").asString());

        List<String> eventTypes = StreamSupport.stream(
                        approval.get("events").spliterator(), false
                )
                .map(event -> event.get("eventType").asString())
                .toList();

        assertEquals(
                List.of(
                        "REFUND_APPROVAL_REQUESTED",
                        "REFUND_APPROVAL_EXPIRED"
                ),
                eventTypes
        );
    }

    private long createPayment() {

        Long customerId = createCustomer();
        String unique = UUID.randomUUID().toString();

        String orderBody = objectMapper.writeValueAsString(Map.of(
                "customerId", customerId,
                "currency", "EUR",
                "items", List.of(Map.of(
                        "productId", "SKU-EXPIRY-" + unique.substring(0, 8),
                        "productName", "Approval expiry test item",
                        "quantity", 1,
                        "unitPrice", new BigDecimal("100.00")
                ))
        ));

        ResponseEntity<String> order = http.postForEntity(
                "/api/v1/orders",
                new HttpEntity<>(orderBody, jsonHeaders()),
                String.class
        );

        assertEquals(HttpStatus.CREATED, order.getStatusCode());

        long orderId = objectMapper.readTree(order.getBody()).get("id").asLong();

        String paymentBody = objectMapper.writeValueAsString(
                Map.of("providerReference", "PAY-EXPIRY-" + unique)
        );

        ResponseEntity<String> payment = http.postForEntity(
                "/api/v1/orders/" + orderId + "/payment",
                new HttpEntity<>(paymentBody, jsonHeaders()),
                String.class
        );

        assertEquals(HttpStatus.CREATED, payment.getStatusCode());

        return objectMapper.readTree(payment.getBody()).get("id").asLong();
    }
}
