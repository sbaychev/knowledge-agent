package com.stefan.knowledgeagent.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeRetrievalServiceTest {

    @Test
    void searchesWithConfiguredTopKAndSimilarityThreshold() {

        VectorStore vectorStore = mock(VectorStore.class);

        Document result = new Document(
                "doc-1",
                "Return requests may be submitted within 30 calendar days.",
                Map.of(
                        "source_id", "refund-policy",
                        "source_name", "Refund Policy",
                        "source_version", "1.0",
                        "chunk_index", 0
                )
        );

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(result));

        KnowledgeRetrievalService service =
                new KnowledgeRetrievalService(vectorStore, 5, 0.35);

        List<Document> results = service.search("return window");

        assertThat(results).containsExactly(result);

        ArgumentCaptor<SearchRequest> captor =
                ArgumentCaptor.forClass(SearchRequest.class);

        verify(vectorStore).similaritySearch(captor.capture());

        SearchRequest request = captor.getValue();

        assertThat(request.getQuery()).isEqualTo("return window");
        assertThat(request.getTopK()).isEqualTo(5);
        assertThat(request.getSimilarityThreshold()).isEqualTo(0.35);
    }

    @Test
    void rejectsBlankQueryWithoutCallingVectorStore() {

        VectorStore vectorStore = mock(VectorStore.class);

        KnowledgeRetrievalService service =
                new KnowledgeRetrievalService(vectorStore, 5, 0.35);

        assertThatThrownBy(() -> service.search("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");

        verifyNoInteractions(vectorStore);
    }
}
