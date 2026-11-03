
package com.stefan.knowledgeagent.approval;

import com.stefan.knowledgeagent.api.ApiIntegrationTestBase;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefundApprovalIntegrationTest extends ApiIntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RefundApprovalEventRepository eventRepository;

    @Test
    void requestApprovalThenApproveSubmitsExactlyOneRefundAndAuditsTheDecision() throws Exception {

        long paymentId = createPayment();

        String idempotencyKey = "approval-" + UUID.randomUUID();

        ResponseEntity<String> requested = requestApproval(
                paymentId,
                new BigDecimal("25.00"),
                "Partial refund for damaged packaging",
                idempotencyKey,
                "agent",
                "agent-test-password"
        );

        assertEquals(HttpStatus.CREATED, requested.getStatusCode());

        JsonNode requestJson = objectMapper.readTree(requested.getBody());
        UUID approvalId = UUID.fromString(requestJson.get("id").asText());

        assertEquals("PENDING", requestJson.get("status").asText());
        assertEquals("agent", requestJson.get("requesterSubject").asText());

        // The same principal, key and normalized request return the existing approval.
        ResponseEntity<String> repeated = requestApproval(
                paymentId,
                new BigDecimal("25.00"),
                "Partial refund for damaged packaging",
                idempotencyKey,
                "agent",
                "agent-test-password"
        );

        assertEquals(HttpStatus.CREATED, repeated.getStatusCode());
        assertEquals(
                approvalId.toString(),
                objectMapper.readTree(repeated.getBody()).get("id").asText()
        );

        // An agent may request approval but cannot make the decision.
        ResponseEntity<String> agentApproval = postDecision(
                approvalId,
                "approve",
                null,
                "agent",
                "agent-test-password"
        );

        assertEquals(HttpStatus.FORBIDDEN, agentApproval.getStatusCode());

        ResponseEntity<String> approved = postDecision(
                approvalId,
                "approve",
                null,
                "approver",
                "approver-test-password"
        );

        assertEquals(HttpStatus.OK, approved.getStatusCode());

        JsonNode approvedJson = objectMapper.readTree(approved.getBody());
        assertEquals("SUBMITTED", approvedJson.get("status").asText());

        long refundId = approvedJson.get("refundId").asLong();

        List<String> eventTypes = StreamSupport.stream(
                        approvedJson.get("events").spliterator(), false
                )
                .map(event -> event.get("eventType").asText())
                .toList();

        assertEquals(
                List.of(
                        "REFUND_APPROVAL_REQUESTED",
                        "REFUND_APPROVAL_APPROVED",
                        "REFUND_SUBMITTED"
                ),
                eventTypes
        );

        // Approval submits a refund request; it does not claim provider completion.
        assertEquals(
                "REQUESTED",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM refunds WHERE id = ?",
                        String.class,
                        refundId
                )
        );

        // Retrying the same approval decision is idempotent.
        ResponseEntity<String> repeatedApproval = postDecision(
                approvalId,
                "approve",
                null,
                "approver",
                "approver-test-password"
        );

        assertEquals(HttpStatus.OK, repeatedApproval.getStatusCode());
        assertEquals(
                refundId,
                objectMapper.readTree(repeatedApproval.getBody())
                        .get("refundId").asLong()
        );

        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM refunds WHERE payment_id = ?",
                        Long.class,
                        paymentId
                )
        );

        assertEquals(
                3,
                eventRepository
                        .findByApprovalRequestIdOrderBySequenceNumberAsc(approvalId)
                        .size()
        );
    }

    @Test
    void concurrentApprovalRetriesCreateOnlyOneRefund() throws Exception {

        long paymentId = createPayment();

        ResponseEntity<String> created = requestApproval(
                paymentId,
                new BigDecimal("35.00"),
                "Concurrent approval retry",
                "concurrent-" + UUID.randomUUID(),
                "agent",
                "agent-test-password"
        );

        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        UUID approvalId = UUID.fromString(
                objectMapper.readTree(created.getBody()).get("id").asText()
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {

            Future<ResponseEntity<String>> first = executor.submit(() -> {
                start.await();
                return postDecision(
                        approvalId,
                        "approve",
                        null,
                        "approver",
                        "approver-test-password"
                );
            });

            Future<ResponseEntity<String>> second = executor.submit(() -> {
                start.await();
                return postDecision(
                        approvalId,
                        "approve",
                        null,
                        "approver",
                        "approver-test-password"
                );
            });

            start.countDown();

            ResponseEntity<String> firstResponse = first.get();
            ResponseEntity<String> secondResponse = second.get();

            assertEquals(HttpStatus.OK, firstResponse.getStatusCode());
            assertEquals(HttpStatus.OK, secondResponse.getStatusCode());

            long firstRefundId = objectMapper.readTree(firstResponse.getBody())
                    .get("refundId").asLong();

            long secondRefundId = objectMapper.readTree(secondResponse.getBody())
                    .get("refundId").asLong();

            assertEquals(firstRefundId, secondRefundId);

            assertEquals(
                    1L,
                    jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM refunds WHERE payment_id = ?",
                            Long.class,
                            paymentId
                    )
            );

            assertEquals(
                    3,
                    eventRepository
                            .findByApprovalRequestIdOrderBySequenceNumberAsc(approvalId)
                            .size()
            );
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void preventsSelfApprovalAndAllowsRejectionWithoutCreatingARefund() throws Exception {

        long paymentId = createPayment();

        ResponseEntity<String> requestedByApprover = requestApproval(
                paymentId,
                new BigDecimal("15.00"),
                "Customer returned item",
                "self-approval-" + UUID.randomUUID(),
                "approver",
                "approver-test-password"
        );

        UUID selfApprovalId = UUID.fromString(
                objectMapper.readTree(requestedByApprover.getBody())
                        .get("id").asString()
        );

        ResponseEntity<String> selfDecision = postDecision(
                selfApprovalId,
                "approve",
                null,
                "approver",
                "approver-test-password"
        );

        assertEquals(HttpStatus.FORBIDDEN, selfDecision.getStatusCode());

        ResponseEntity<String> created = requestApproval(
                paymentId,
                new BigDecimal("20.00"),
                "Customer changed their mind",
                "reject-" + UUID.randomUUID(),
                "agent",
                "agent-test-password"
        );

        UUID rejectionId = UUID.fromString(
                objectMapper.readTree(created.getBody()).get("id").asString()
        );

        ResponseEntity<String> rejected = postDecision(
                rejectionId,
                "reject",
                objectMapper.writeValueAsString(
                        Map.of("reason", "The return window has expired.")
                ),
                "approver",
                "approver-test-password"
        );

        assertEquals(HttpStatus.OK, rejected.getStatusCode());

        JsonNode rejectedJson = objectMapper.readTree(rejected.getBody());

        assertEquals("REJECTED", rejectedJson.get("status").asString());
        assertEquals("The return window has expired.", rejectedJson.get("rejectionReason").asString());

        assertEquals(
                0L,
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM refunds WHERE payment_id = ?",
                        Long.class,
                        paymentId
                )
        );

        // A terminal approval cannot be decided again; expose a domain conflict, not HTTP 500.
        ResponseEntity<String> approveRejected = postDecision(
                rejectionId,
                "approve",
                null,
                "approver",
                "approver-test-password"
        );

        assertEquals(HttpStatus.CONFLICT, approveRejected.getStatusCode());
    }

    @Test
    void rejectsConflictingIdempotencyReuseWithoutCreatingARefund() throws Exception {

        long paymentId = createPayment();
        String key = "conflict-" + UUID.randomUUID();

        ResponseEntity<String> created = requestApproval(
                paymentId,
                new BigDecimal("10.00"),
                "Customer requested a refund",
                key,
                "agent",
                "agent-test-password"
        );

        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        ResponseEntity<String> conflictingRetry = requestApproval(
                paymentId,
                new BigDecimal("11.00"),
                "Different amount with reused key",
                key,
                "agent",
                "agent-test-password"
        );

        assertEquals(HttpStatus.CONFLICT, conflictingRetry.getStatusCode());

        assertEquals(
                0L,
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM refunds WHERE payment_id = ?",
                        Long.class,
                        paymentId
                )
        );
    }

    @Test
    void approvalEventHistoryIsAppendOnlyAtTheDatabaseBoundary() throws Exception {

        long paymentId = createPayment();

        ResponseEntity<String> created = requestApproval(
                paymentId,
                new BigDecimal("5.00"),
                "Audit immutability test",
                "append-only-" + UUID.randomUUID(),
                "agent",
                "agent-test-password"
        );

        UUID approvalId = UUID.fromString(objectMapper.readTree(created.getBody()).get("id").asString());

        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update(
                        """
                                UPDATE refund_approval_events
                                SET actor_subject = 'tampered'
                                WHERE approval_request_id = ?
                                """,
                        approvalId
                )
        );
    }

    private ResponseEntity<String> requestApproval(
            long paymentId,
            BigDecimal amount,
            String reason,
            String idempotencyKey,
            String username,
            String password
    ) {

        String request = objectMapper.writeValueAsString(Map.of(
                "paymentId", paymentId,
                "amount", amount,
                "reason", reason
        ));

        HttpHeaders headers = jsonHeaders();
        headers.set("Idempotency-Key", idempotencyKey);

        return http.withBasicAuth(username, password).postForEntity(
                "/api/v1/approvals/refunds",
                new HttpEntity<>(request, headers),
                String.class
        );
    }

    private ResponseEntity<String> postDecision(
            UUID approvalId,
            String decision,
            String body,
            String username,
            String password
    ) {

        HttpHeaders headers = jsonHeaders();
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        return http.withBasicAuth(username, password).exchange(
                "/api/v1/approvals/" + approvalId + "/" + decision,
                HttpMethod.POST,
                entity,
                String.class
        );
    }

    private long createPayment() {

        Long customerId = createCustomer();
        String unique = UUID.randomUUID().toString();

        String orderRequest = objectMapper.writeValueAsString(Map.of(
                "customerId", customerId,
                "currency", "EUR",
                "items", List.of(Map.of(
                        "productId", "SKU-APPROVAL-" + unique.substring(0, 8),
                        "productName", "Refund approval test item",
                        "quantity", 1,
                        "unitPrice", new BigDecimal("100.00")
                ))
        ));

        ResponseEntity<String> order = http.postForEntity(
                "/api/v1/orders",
                new HttpEntity<>(orderRequest, jsonHeaders()),
                String.class
        );

        assertEquals(HttpStatus.CREATED, order.getStatusCode());

        long orderId = objectMapper.readTree(order.getBody()).get("id").asLong();

        String paymentRequest = objectMapper.writeValueAsString(
                Map.of("providerReference", "PAY-APPROVAL-" + unique)
        );

        ResponseEntity<String> payment = http.postForEntity(
                "/api/v1/orders/" + orderId + "/payment",
                new HttpEntity<>(paymentRequest, jsonHeaders()),
                String.class
        );

        assertEquals(HttpStatus.CREATED, payment.getStatusCode());
        assertNotNull(payment.getBody());

        return objectMapper.readTree(payment.getBody()).get("id").asLong();
    }
}
