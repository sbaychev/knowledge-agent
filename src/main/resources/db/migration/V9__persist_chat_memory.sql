CREATE TABLE spring_ai_chat_memory
(
    conversation_id VARCHAR(36) NOT NULL,
    content          TEXT NOT NULL,
    type             VARCHAR(10) NOT NULL
        CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp"      TIMESTAMP NOT NULL,
    sequence_id      BIGINT NOT NULL
);

CREATE INDEX idx_spring_ai_chat_memory_conversation_timestamp
    ON spring_ai_chat_memory (conversation_id, "timestamp");

CREATE INDEX idx_spring_ai_chat_memory_conversation_sequence
    ON spring_ai_chat_memory (conversation_id, sequence_id);
