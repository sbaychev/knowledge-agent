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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    void discoversAndInvokesOrderLookupThroughMcpHttp()
            throws Exception {

        when(orderLookupTool.lookupOrder(42L))
                .thenReturn("Order found. Order ID: 42");

        // 1. Initialize the MCP session.
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

        assertThat(initializeResponse.statusCode()).isEqualTo(200);

        JsonNode initializeResult =
                parseRpcResponse(initializeResponse.body())
                        .path("result");

        assertThat(initializeResult.path("protocolVersion").asText())
                .isNotBlank();

        String negotiatedProtocolVersion =
                initializeResult.path("protocolVersion").asText();

        String sessionId = initializeResponse.headers()
                .firstValue("Mcp-Session-Id")
                .orElse(null);

        // 2. Complete the MCP initialization handshake.
        HttpResponse<String> initializedResponse = postRpc(
                "notifications/initialized",
                null,
                null,
                sessionId,
                negotiatedProtocolVersion
        );

        assertThat(initializedResponse.statusCode())
                .isIn(200, 202, 204);

        // 3. Discover the tools actually exposed by the server.
        HttpResponse<String> listResponse = postRpc(
                "tools/list",
                2,
                null,
                sessionId,
                negotiatedProtocolVersion
        );

        assertThat(listResponse.statusCode()).isEqualTo(200);

        JsonNode listResult =
                parseRpcResponse(listResponse.body());

        assertThat(listResult.path("error").isMissingNode()).isTrue();

        List<String> toolNames = new ArrayList<>();

        listResult.path("result")
                .path("tools")
                .forEach(tool ->
                        toolNames.add(tool.path("name").asText())
                );

        assertThat(toolNames).containsExactlyInAnyOrder(
                "lookup_order",
                "lookup_shipment_by_order",
                "lookup_payment_by_order",
                "lookup_refunds_by_order"
        );

        // 4. Invoke a discovered tool through the MCP protocol.
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

        assertThat(callResponse.statusCode()).isEqualTo(200);

        JsonNode callResult =
                parseRpcResponse(callResponse.body());

        assertThat(callResult.path("error").isMissingNode()).isTrue();

        JsonNode result = callResult.path("result");

        assertThat(result.path("isError").asBoolean(false)).isFalse();
        assertThat(result.path("content").get(0).path("text").asText())
                .contains("Order found. Order ID: 42");

        verify(orderLookupTool).lookupOrder(42L);
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

        // Streamable HTTP can carry a JSON-RPC response as an SSE event.
        if (normalized.startsWith("event:")
                || normalized.contains("\ndata:")) {

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
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
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
