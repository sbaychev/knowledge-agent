package com.stefan.knowledgeagent.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Profile("rag")
public class KnowledgeRetrievalService {

    private final VectorStore vectorStore;
    private final int topK;
    private final double similarityThreshold;

    public KnowledgeRetrievalService(
            VectorStore vectorStore,
            @Value("${knowledge.retrieval.top-k:5}") int topK,
            @Value("${knowledge.retrieval.similarity-threshold:0.35}")
            double similarityThreshold
    ) {

        if (topK < 1 || topK > 10) {

            throw new IllegalArgumentException(
                    "knowledge.retrieval.top-k must be between 1 and 10."
            );
        }

        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {

            throw new IllegalArgumentException(
                    "knowledge.retrieval.similarity-threshold must be between 0 and 1."
            );
        }

        this.vectorStore = vectorStore;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    public List<Document> search(String query) {

        if (!StringUtils.hasText(query)) {

            throw new IllegalArgumentException(
                    "Knowledge search query must not be blank."
            );
        }

        String normalizedQuery = query.strip();

        if (normalizedQuery.length() > 1000) {

            throw new IllegalArgumentException(
                    "Knowledge search query must not exceed 1000 characters."
            );
        }

        SearchRequest request = SearchRequest.builder()
                .query(normalizedQuery)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();

        return vectorStore.similaritySearch(request);
    }

}
