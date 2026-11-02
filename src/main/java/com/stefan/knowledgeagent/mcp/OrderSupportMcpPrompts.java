package com.stefan.knowledgeagent.mcp;

import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Reusable, read-only investigation prompt published over MCP.
 *
 * <p>The prompt provides a procedure; it does not execute the procedure. The
 * consuming client is still responsible for invoking available tools and for
 * deciding what to show to its user.</p>
 */
@Component
@Profile("mcp-server")
public class OrderSupportMcpPrompts {

    @McpPrompt(
            name = "investigate-order",
            description = """
                    Creates a read-only investigation plan for a specific order.
                    The consuming model should use the available lookup tools
                    for current transactional facts and must not mutate state.
                    """
    )
    public GetPromptResult investigateOrder(
            @McpArg(
                    name = "orderId",
                    description = "Positive internal numeric order ID",
                    required = true
            )
            String orderId
    ) {
        long parsedOrderId;

        try {
            parsedOrderId = Long.parseLong(orderId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "orderId must be a positive numeric order ID.",
                    exception
            );
        }

        if (parsedOrderId <= 0) {
            throw new IllegalArgumentException(
                    "orderId must be a positive numeric order ID."
            );
        }

        String instructions = """
                Investigate order %d for a customer-support response.

                Follow this read-only procedure:
                1. Call lookup_order to confirm the order exists and inspect its
                   current status, customer ID, total, currency, and items.
                2. If shipment details are relevant, call
                   lookup_shipment_by_order using the same order ID.
                3. If payment status or payment amount is relevant, call
                   lookup_payment_by_order using the same order ID.
                4. If refund history is relevant, call
                   lookup_refunds_by_order using the same order ID.
                5. Distinguish observed facts from interpretation. If a tool
                   reports that data is missing, say so rather than guessing.
                6. Use knowledge://policy/{policyId} resources only for
                   documented policy text; they do not prove the live state
                   of an order, payment, shipment, or refund.

                Safety constraints:
                - Use only the read-only lookup tools available to you.
                - Do not create, cancel, dispatch, deliver, charge, or refund
                  anything.
                - Do not claim that a mutation or approval has happened.
                - The policy files distributed with this demo are illustrative,
                  not an authoritative customer policy.
                """.formatted(parsedOrderId);

        return GetPromptResult.builder(
                        java.util.List.of(
                                new PromptMessage(
                                        Role.USER,
                                        TextContent.builder(instructions).build()
                                )
                        )
                )
                .description("Read-only investigation procedure for order " + parsedOrderId)
                .build();
    }
}
