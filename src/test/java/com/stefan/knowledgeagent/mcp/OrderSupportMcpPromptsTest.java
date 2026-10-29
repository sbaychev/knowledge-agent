package com.stefan.knowledgeagent.mcp;

import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderSupportMcpPromptsTest {

    private final OrderSupportMcpPrompts prompts = new OrderSupportMcpPrompts();

    @Test
    void createsParameterizedReadOnlyInvestigationPrompt() {

        GetPromptResult result = prompts.investigateOrder("42");

        assertThat(result.description()).contains("order 42");
        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().getFirst().content())
                .isInstanceOf(TextContent.class);

        TextContent content = (TextContent) result.messages().getFirst().content();

        assertThat(content.text())
                .contains("Investigate order 42")
                .contains("lookup_order")
                .contains("lookup_shipment_by_order")
                .contains("Do not create, cancel, dispatch, deliver, charge, or refund")
                .contains("illustrative");
    }

    @Test
    void rejectsInvalidOrderIdentifiersBeforeCreatingPrompt() {

        assertThatThrownBy(() -> prompts.investigateOrder("0"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> prompts.investigateOrder("-1"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> prompts.investigateOrder("not-an-order"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> prompts.investigateOrder("999999999999999999999999"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
