package com.stefan.knowledgeagent.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stefan.knowledgeagent.mcp.OrderSupportMcpTools;
import com.stefan.knowledgeagent.tools.OrderLookupTool;
import com.stefan.knowledgeagent.tools.PaymentLookupTool;
import com.stefan.knowledgeagent.tools.RefundLookupTool;
import com.stefan.knowledgeagent.tools.ShipmentLookupTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = McpServerHttpIntegrationTest.McpTestApplication.class,
        properties = {
                "spring.ai.model.chat=none",
                "spring.ai.model.embedding=none",
                "spring.ai.vectorstore.type=none",
                "spring.ai.mcp.server.protocol=STREAMABLE",
                "spring.ai.mcp.server.type=SYNC",
                "spring.ai.mcp.server.annotation-scanner.enabled=true"
        }
)
@ActiveProfiles("mcp-server")
class McpServerHttpIntegrationTest {

    private static final String PROTOCOL_VERSION = "2025-06-18";

    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private OrderLookupTool orderLookupTool;

    @Test
    void exposesMcpToolsResourcesAndPromptsAndRejectsInvalidInputs()
            throws Exception {

        when(orderLookupTool.lookupOrder(42L))
                .thenReturn("Order found. Order ID: 42");

        HttpResponse<String> initializeResponse = postRpc(
                "initialize",
                1,
                Map.of(
                        "protocolVersion", PROTOCOL_VERSION,
                        "capabilities", Map.of(),
                        "clientInfo", Map.of(
                                "name", "knowledge-agent-test-client",
                                "version", "1.0.0"
                        )
                ),
                null,
                null
        );

        assertThat(initializeResponse.statusCode())
                .as("MCP initialize response: %s", initializeResponse.body())
                .isEqualTo(200);

        JsonNode initializeEnvelope =
                parseRpcResponse(initializeResponse.body());

        assertThat(initializeEnvelope.path("error").isMissingNode()).isTrue();
        JsonNode initializeResult = initializeEnvelope.path("result");
        assertThat(initializeResult.path("protocolVersion").asText())
                .isNotBlank();

        String negotiatedProtocolVersion =
                initializeResult.path("protocolVersion").asText();

        String sessionId = initializeResponse.headers()
                .firstValue("Mcp-Session-Id")
                .orElse(null);

        HttpResponse<String> initializedResponse = postRpc(
                "notifications/initialized",
                null,
                null,
                sessionId,
                negotiatedProtocolVersion
        );

        assertThat(initializedResponse.statusCode())
                .as("MCP initialized notification: %s", initializedResponse.body())
                .isIn(200, 202, 204);

        HttpResponse<String> listResponse = postRpc(
                "tools/list",
                2,
                null,
                sessionId,
                negotiatedProtocolVersion
        );

        assertThat(listResponse.statusCode())
                .as("MCP tools/list response: %s", listResponse.body())
                .isEqualTo(200);

        JsonNode listEnvelope = parseRpcResponse(listResponse.body());
        assertThat(listEnvelope.path("error").isMissingNode()).isTrue();

        List<String> toolNames = new ArrayList<>();
        listEnvelope.path("result").path("tools").forEach(tool ->
                toolNames.add(tool.path("name").asText())
        );

        assertThat(toolNames).containsExactlyInAnyOrder(
                "lookup_order",
                "lookup_shipment_by_order",
                "lookup_payment_by_order",
                "lookup_refunds_by_order"
        );

        HttpResponse<String> callResponse = postRpc(
                "tools/call",
                3,
                Map.of(
                        "name", "lookup_order",
                        "arguments", Map.of("orderId", 42L)
                ),
                sessionId,
                negotiatedProtocolVersion
        );

        assertThat(callResponse.statusCode())
                .as("MCP tools/call response: %s", callResponse.body())
                .isEqualTo(200);

        JsonNode callEnvelope = parseRpcResponse(callResponse.body());
        assertThat(callEnvelope.path("error").isMissingNode()).isTrue();

        JsonNode result = callEnvelope.path("result");
        assertThat(result.path("isError").asBoolean(false)).isFalse();
        assertThat(result.path("content").get(0).path("text").asText())
                .contains("Order found. Order ID: 42");

        verify(orderLookupTool).lookupOrder(42L);

        // 5. Discover the policy catalog resource separately from executable tools.
        HttpResponse<String> resourcesResponse = postRpc(
                "resources/list",
                4,
                Map.of(),
                sessionId,
                negotiatedProtocolVersion
        );

        assertThat(resourcesResponse.statusCode())
                .as("MCP resources/list response: %s", resourcesResponse.body())
                .isEqualTo(200);

        JsonNode resourcesEnvelope = parseRpcResponse(resourcesResponse.body());
        assertThat(resourcesEnvelope.path("error").isMissingNode()).isTrue();
        assertThat(resourcesEnvelope.path("result").path("resources").toString())
                .contains("knowledge://policies");

        // 6. Discover the allow-listed resource template and read a real classpath document.
        HttpResponse<String> templatesResponse = postRpc(
                "resources/templates/list",
                5,
                Map.of(),
                sessionId,
                negotiatedProtocolVersion
        );

        assertThat(templatesResponse.statusCode())
                .as("MCP resources/templates/list response: %s", templatesResponse.body())
                .isEqualTo(200);

        JsonNode templatesEnvelope = parseRpcResponse(templatesResponse.body());
        assertThat(templatesEnvelope.path("error").isMissingNode()).isTrue();
        assertThat(templatesEnvelope.path("result").path("resourceTemplates").toString())
                .contains("knowledge://policy/{policyId}");

        HttpResponse<String> refundPolicyResponse = postRpc(
                "resources/read",
                6,
                Map.of("uri", "knowledge://policy/refund-policy"),
                sessionId,
                negotiatedProtocolVersion
        );

        assertThat(refundPolicyResponse.statusCode())
                .as("MCP resources/read response: %s", refundPolicyResponse.body())
                .isEqualTo(200);

        JsonNode refundPolicyEnvelope = parseRpcResponse(refundPolicyResponse.body());
        assertThat(refundPolicyEnvelope.path("error").isMissingNode()).isTrue();
        String refundPolicyText = refundPolicyEnvelope.path("result")
                .path("contents").get(0).path("text").asText();

        assertThat(refundPolicyText)
                .contains("# Refund Policy")
                .contains("30 calendar days")
                .contains("demonstration policy content");

        // 7. Discover the reusable order-investigation prompt.
        HttpResponse<String> promptsResponse = postRpc(
                "prompts/list",
                7,
                Map.of(),
                sessionId,
                negotiatedProtocolVersion
        );

        assertThat(promptsResponse.statusCode())
                .as("MCP prompts/list response: %s", promptsResponse.body())
                .isEqualTo(200);

        JsonNode promptsEnvelope = parseRpcResponse(promptsResponse.body());
        assertThat(promptsEnvelope.path("error").isMissingNode()).isTrue();
        assertThat(promptsEnvelope.path("result").path("prompts").toString())
                .contains("investigate-order");

        HttpResponse<String> promptResponse = postRpc(
                "prompts/get",
                8,
                Map.of(
                        "name", "investigate-order",
                        "arguments", Map.of("orderId", "42")
                ),
                sessionId,
                negotiatedProtocolVersion
        );

        assertThat(promptResponse.statusCode())
                .as("MCP prompts/get response: %s", promptResponse.body())
                .isEqualTo(200);

        JsonNode promptEnvelope = parseRpcResponse(promptResponse.body());
        assertThat(promptEnvelope.path("error").isMissingNode()).isTrue();
        String promptText = promptEnvelope.path("result")
                .path("messages").get(0).path("content").path("text").asText();

        assertThat(promptText)
                .contains("Investigate order 42")
                .contains("lookup_order")
                .contains("Do not create, cancel, dispatch, deliver, charge, or refund");

        // Invalid resource IDs must not be resolved to arbitrary classpath files.
        HttpResponse<String> invalidPolicyResponse = postRpc(
                "resources/read",
                9,
                Map.of("uri", "knowledge://policy/not-allow-listed"),
                sessionId,
                negotiatedProtocolVersion
        );

        assertThat(invalidPolicyResponse.statusCode())
                .as("MCP resources/read with an unknown policy ID: %s",
                        invalidPolicyResponse.body())
                .isEqualTo(200);

        JsonNode invalidPolicyEnvelope =
                parseRpcResponse(invalidPolicyResponse.body());

        assertThat(invalidPolicyEnvelope.path("error").isMissingNode())
                .as("Unknown policy IDs must be returned as an MCP protocol error")
                .isFalse();

        // Malformed arguments must fail before invoking the application-owned tool.
        clearInvocations(orderLookupTool);

        HttpResponse<String> malformedArgumentsResponse = postRpc(
                "tools/call",
                10,
                Map.of(
                        "name", "lookup_order",
                        "arguments", Map.of("orderId", "not-a-number")
                ),
                sessionId,
                negotiatedProtocolVersion
        );

        assertThat(malformedArgumentsResponse.statusCode())
                .as("MCP tools/call with a malformed order ID: %s",
                        malformedArgumentsResponse.body())
                .isEqualTo(200);

        JsonNode malformedArgumentsEnvelope =
                parseRpcResponse(malformedArgumentsResponse.body());

        boolean rejectedAsRpcError =
                !malformedArgumentsEnvelope.path("error").isMissingNode();
        boolean rejectedAsToolError =
                malformedArgumentsEnvelope.path("result")
                        .path("isError")
                        .asBoolean(false);

        assertThat(rejectedAsRpcError || rejectedAsToolError)
                .as("Malformed tool arguments must be rejected by the MCP server")
                .isTrue();

        verifyNoInteractions(orderLookupTool);
    }

    private HttpResponse<String> postRpc(
            String method,
            Integer id,
            Map<String, Object> params,
            String sessionId,
            String protocolVersion
    ) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        if (id != null) {
            payload.put("id", id);
        }
        payload.put("method", method);
        if (params != null) {
            payload.put("params", params);
        }

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(payload)
                ));

        if (sessionId != null) {
            request.header("Mcp-Session-Id", sessionId);
        }
        if (protocolVersion != null) {
            request.header("MCP-Protocol-Version", protocolVersion);
        }

        return HTTP_CLIENT.send(
                request.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private JsonNode parseRpcResponse(String body) throws Exception {
        String normalized = body.strip();
        if (normalized.startsWith("event:") || normalized.contains("\ndata:")) {
            for (String line : normalized.split("\\R")) {
                if (line.startsWith("data:")) {
                    return objectMapper.readTree(
                            line.substring("data:".length()).strip()
                    );
                }
            }
        }
        return objectMapper.readTree(normalized);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(
            exclude = {
                    DataSourceAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class,
                    FlywayAutoConfiguration.class
            },
            excludeName = {
                    "org.springframework.ai.model.chat.memory.repository.jdbc.autoconfigure.JdbcChatMemoryRepositoryAutoConfiguration"
            }
    )
    @ComponentScan(basePackageClasses = OrderSupportMcpTools.class)
    static class McpTestApplication {

        @Bean
        OrderLookupTool orderLookupTool() {
            return mock(OrderLookupTool.class);
        }

        @Bean
        ShipmentLookupTool shipmentLookupTool() {
            return mock(ShipmentLookupTool.class);
        }

        @Bean
        PaymentLookupTool paymentLookupTool() {
            return mock(PaymentLookupTool.class);
        }

        @Bean
        RefundLookupTool refundLookupTool() {
            return mock(RefundLookupTool.class);
        }
    }
}
