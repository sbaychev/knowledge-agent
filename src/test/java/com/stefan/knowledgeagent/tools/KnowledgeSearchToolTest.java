package com.stefan.knowledgeagent.tools;

import com.stefan.knowledgeagent.knowledge.KnowledgeRetrievalService;
import com.stefan.knowledgeagent.observability.AgentObservability;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeSearchToolTest {

    @Test
    void returnsExcerptWithSourceAndVersionForCitation() {

        KnowledgeRetrievalService retrievalService =
                mock(KnowledgeRetrievalService.class);

        when(retrievalService.search("return policy"))
                .thenReturn(List.of(new Document(
                        "doc-1",
                        "Return requests may be submitted within 30 calendar days.",
                        Map.of(
                                "source_id", "refund-policy",
                                "source_name", "Refund Policy",
                                "source_version", "1.0",
                                "chunk_index", 0
                        )
                )));

        KnowledgeSearchTool tool = new KnowledgeSearchTool(
                retrievalService,
                AgentObservability.noop()
        );

        String result = tool.searchKnowledgeBase("return policy");

        assertThat(result)
                .contains("SOURCE: Refund Policy")
                .contains("SOURCE_ID: refund-policy")
                .contains("VERSION: 1.0")
                .contains("CHUNK: 0")
                .contains("30 calendar days");
    }

    @Test
    void explainsWhenNoRelevantKnowledgeIsFound() {

        KnowledgeRetrievalService retrievalService =
                mock(KnowledgeRetrievalService.class);

        when(retrievalService.search("unknown policy"))
                .thenReturn(List.of());

        KnowledgeSearchTool tool = new KnowledgeSearchTool(
                retrievalService,
                AgentObservability.noop()
        );

        String result = tool.searchKnowledgeBase("unknown policy");

        assertThat(result)
                .contains("NO_RELEVANT_KNOWLEDGE_FOUND")
                .contains("Do not invent policy details");
    }
}
