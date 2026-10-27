CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE knowledge_vectors
(
    id        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    content   TEXT NOT NULL,
    metadata  JSON NOT NULL DEFAULT '{}'::json,
    embedding vector(1536) NOT NULL
);

CREATE INDEX idx_knowledge_vectors_embedding_hnsw
    ON knowledge_vectors
    USING hnsw (embedding vector_cosine_ops);

CREATE TABLE knowledge_sources
(
    source_id      VARCHAR(120) PRIMARY KEY,
    source_name    VARCHAR(255) NOT NULL,
    source_version VARCHAR(80) NOT NULL,
    sha256         CHAR(64) NOT NULL,
    chunk_count    INTEGER NOT NULL CHECK (chunk_count > 0),
    ingested_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
