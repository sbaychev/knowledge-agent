package com.stefan.knowledgeagent.mcp;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Exposes the demonstration policy corpus as MCP resources.
 *
 * <p>Resources deliberately use an allow-list rather than mapping an arbitrary
 * URI segment to a classpath path. The resource surface is read-only and does
 * not depend on embeddings or the optional RAG profile.</p>
 */
@Component
@Profile("mcp-server")
public class SupportPolicyMcpResources {

    private static final Map<String, String> POLICY_RESOURCES = Map.of(
            "shipping-policy", "classpath:knowledge/shipping-policy.md",
            "refund-policy", "classpath:knowledge/refund-policy.md",
            "warranty-policy", "classpath:knowledge/warranty-policy.md"
    );

    private final ResourceLoader resourceLoader;

    public SupportPolicyMcpResources(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @McpResource(
            uri = "knowledge://policies",
            name = "Customer support policy catalog",
            description = """
                    Lists the policy resources available from Knowledge Agent.
                    The policies shipped with this repository are illustrative
                    examples and must not be treated as an organization's
                    authoritative customer policy.
                    """,
            mimeType = "text/markdown"
    )
    public String policyCatalog() {
        return """
                # Knowledge Agent policy catalog

                These documents are demonstration data, not approved operational
                policy. Validate them with the policy owner before relying on
                them in a real customer-support process.

                | Policy ID | Document |
                |---|---|
                | shipping-policy | Shipping Policy |
                | refund-policy | Refund Policy |
                | warranty-policy | Warranty Policy |

                Read a specific document using the URI template
                knowledge://policy/{policyId}.

                Policy resources are reference material. Transactional facts
                such as an order's current status, shipment tracking number,
                payment status, or refund history must be obtained from the
                corresponding read-only lookup tool.
                """;
    }

    @McpResource(
            uri = "knowledge://policy/{policyId}",
            name = "Customer support policy document",
            description = """
                    Reads one allow-listed Markdown policy document by policy ID.
                    Supported IDs are shipping-policy, refund-policy and
                    warranty-policy. Documents are demonstration examples.
                    """,
            mimeType = "text/markdown"
    )
    public String readPolicy(String policyId) {
        String location = POLICY_RESOURCES.get(policyId);

        if (location == null) {
            throw new IllegalArgumentException(
                    "Unknown policy ID. Read knowledge://policies for the "
                            + "supported policy IDs."
            );
        }

        Resource resource = resourceLoader.getResource(location);

        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to read the configured policy resource: " + policyId,
                    exception
            );
        }
    }
}
