package com.stefan.knowledgeagent.mcp;

import com.stefan.knowledgeagent.tools.KnowledgeSearchTool;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("mcp-server & rag")
public class KnowledgeBaseMcpTools {

    private final KnowledgeSearchTool knowledgeSearchTool;

    public KnowledgeBaseMcpTools(
            KnowledgeSearchTool knowledgeSearchTool
    ) {
        this.knowledgeSearchTool = knowledgeSearchTool;
    }

    @McpTool(
            name = "search_knowledge_base",
            description = """
                    Search versioned policy and process documents.
                    Returns relevant document excerpts with source ID and version.
                    This tool does not provide current order, shipment, payment,
                    or refund status.
                    """,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    openWorldHint = false
            )
    )
    public String searchKnowledgeBase(
            @McpToolParam(
                    description = "A focused question about a documented policy",
                    required = true
            )
            String query
    ) {

        return knowledgeSearchTool.searchKnowledgeBase(query);
    }
}
