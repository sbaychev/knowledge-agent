package com.stefan.knowledgeagent.tools;

import com.stefan.knowledgeagent.knowledge.KnowledgeRetrievalService;
import com.stefan.knowledgeagent.observability.AgentObservability;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Profile("rag")
public class KnowledgeSearchTool {

    private final KnowledgeRetrievalService retrievalService;
    private final AgentObservability observability;

    public KnowledgeSearchTool(
            KnowledgeRetrievalService retrievalService,
            AgentObservability observability
    ) {

        this.retrievalService = retrievalService;
        this.observability = observability;

    }

    @Tool(
            name = "search_knowledge_base",
            description = """
                    Search versioned knowledge documents for relevant policy
                    and process information, such as shipping, returns and warranty.
                    This tool retrieves document excerpts; it does not provide
                    live order, shipment, payment or refund status.
                    Cite the returned source name and version in the answer.
                    If no relevant documents are returned, do not invent a policy.
                    This tool is read-only.
                    """
    )
    public String searchKnowledgeBase(
            @ToolParam(
                    description = "A focused question about a documented policy or process"
            )
            String query
    ) {

        return observability.recordToolCall(
                "search_knowledge_base",
                () -> searchInternal(query)
        );
    }

    private String searchInternal(String query) {

        if (!StringUtils.hasText(query)) {

            return "INVALID_KNOWLEDGE_QUERY: A search query is required.";
        }

        List<Document> results = retrievalService.search(query);

        if (results.isEmpty()) {

            return """
                    NO_RELEVANT_KNOWLEDGE_FOUND:
                    No relevant policy excerpts were found for this question.
                    Do not invent policy details. Explain that the available
                    knowledge documents do not establish the answer.
                    """;
        }

        return results.stream()
                .map(this::formatDocument)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private String formatDocument(Document document) {

        Map<String, Object> metadata = document.getMetadata();

        String sourceId = metadataValue(metadata, "source_id");
        String sourceName = metadataValue(metadata, "source_name");
        String version = metadataValue(metadata, "source_version");
        String chunkIndex = metadataValue(metadata, "chunk_index");
        String text = document.getText();

        return """
                SOURCE: %s
                SOURCE_ID: %s
                VERSION: %s
                CHUNK: %s
                
                EXCERPT:
                %s
                """.formatted(
                sourceName,
                sourceId,
                version,
                chunkIndex,
                text == null ? "" : text
        );
    }

    private String metadataValue(
            Map<String, Object> metadata,
            String key
    ) {

        Object value = metadata.get(key);

        return value == null ? "unknown" : value.toString();
    }
}
