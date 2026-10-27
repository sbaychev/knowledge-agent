package com.stefan.knowledgeagent.knowledge;


import jakarta.transaction.Transactional;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("rag")
public class KnowledgeIngestionService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    private final TokenTextSplitter splitter =
            TokenTextSplitter.builder()
                    .withChunkSize(350)
                    .withMinChunkSizeChars(80)
                    .withMinChunkLengthToEmbed(20)
                    .withMaxNumChunks(1000)
                    .withKeepSeparator(true)
                    .build();

    public KnowledgeIngestionService(
            VectorStore vectorStore,
            JdbcTemplate jdbcTemplate
    ) {

        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;

    }

    @Transactional
    public IngestionResult ingest(
            String sourceId,
            String sourceName,
            String sourceVersion,
            String content
    ) {

        validateSource(sourceId, sourceName, sourceVersion, content);

        String checksum = sha256(content);

        List<SourceState> existing = jdbcTemplate.query(
                """
                        SELECT source_name, source_version, sha256, chunk_count
                        FROM knowledge_sources
                        WHERE source_id = ?
                        """,
                (rs, rowNum) -> new SourceState(
                        rs.getString("source_name"),
                        rs.getString("source_version"),
                        rs.getString("sha256"),
                        rs.getInt("chunk_count")
                ),
                sourceId
        );

        if (!existing.isEmpty()) {

            SourceState previous = existing.getFirst();

            if (previous.sha256().equals(checksum)
                    && previous.sourceVersion().equals(sourceVersion)
                    && previous.sourceName().equals(sourceName)) {

                return new IngestionResult(
                        sourceId,
                        checksum,
                        previous.chunkCount(),
                        true
                );
            }
        }

        Map<String, Object> sourceMetadata = Map.of(
                "source_id", sourceId,
                "source_name", sourceName,
                "source_version", sourceVersion,
                "sha256", checksum
        );

        Document sourceDocument = new Document(content, sourceMetadata);

        List<Document> splitDocuments =
                splitter.apply(List.of(sourceDocument));

        List<Document> chunks = new ArrayList<>();

        for (Document splitDocument : splitDocuments) {

            String text = splitDocument.getText();

            if (!StringUtils.hasText(text)) {
                continue;
            }

            int chunkIndex = chunks.size();

            Map<String, Object> metadata =
                    new HashMap<>(sourceMetadata);

            metadata.put("chunk_index", chunkIndex);

            String documentId = UUID.nameUUIDFromBytes(
                    (sourceId + ":" + sourceVersion + ":"
                            + checksum + ":" + chunkIndex)
                            .getBytes(StandardCharsets.UTF_8)
            ).toString();

            chunks.add(new Document(
                    documentId,
                    text,
                    metadata
            ));
        }

        if (chunks.isEmpty()) {

            throw new IllegalArgumentException(
                    "The knowledge source produced no indexable chunks: "
                            + sourceId
            );
        }

        // Remove obsolete chunks for this source, not other documents.
        // sourceId is validated before it is placed in the filter expression.
        vectorStore.delete("source_id == '" + sourceId + "'");

        vectorStore.add(chunks);

        jdbcTemplate.update(
                """
                        INSERT INTO knowledge_sources
                            (source_id, source_name, source_version,
                             sha256, chunk_count, ingested_at)
                        VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        ON CONFLICT (source_id)
                        DO UPDATE SET
                            source_name = EXCLUDED.source_name,
                            source_version = EXCLUDED.source_version,
                            sha256 = EXCLUDED.sha256,
                            chunk_count = EXCLUDED.chunk_count,
                            ingested_at = CURRENT_TIMESTAMP
                        """,
                sourceId,
                sourceName,
                sourceVersion,
                checksum,
                chunks.size()
        );

        return new IngestionResult(
                sourceId,
                checksum,
                chunks.size(),
                false
        );
    }

    private void validateSource(
            String sourceId,
            String sourceName,
            String sourceVersion,
            String content
    ) {

        if (!StringUtils.hasText(sourceId)
                || !sourceId.matches("[a-z0-9][a-z0-9-]{0,119}")) {

            throw new IllegalArgumentException(
                    "Invalid knowledge source ID."
            );
        }

        if (!StringUtils.hasText(sourceName)
                || !StringUtils.hasText(sourceVersion)
                || !StringUtils.hasText(content)) {

            throw new IllegalArgumentException(
                    "Knowledge source name, version and content are required."
            );
        }
    }

    private String sha256(String content) {

        try {

            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));

            return java.util.HexFormat.of().formatHex(digest);

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 is not available in this JVM.",
                    exception
            );
        }
    }

    private record SourceState(
            String sourceName,
            String sourceVersion,
            String sha256,
            int chunkCount
    ) {
    }

    public record IngestionResult(
            String sourceId,
            String checksum,
            int chunkCount,
            boolean skipped
    ) {
    }
}
