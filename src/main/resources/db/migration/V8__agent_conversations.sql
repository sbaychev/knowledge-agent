CREATE TABLE agent_conversations
(
    id         UUID PRIMARY KEY,
    status     VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    closed_at  TIMESTAMPTZ,
    version    BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_agent_conversations_status
        CHECK (status IN ('ACTIVE', 'CLOSED')),

    CONSTRAINT chk_agent_conversations_closed_at
        CHECK (
            (status = 'ACTIVE' AND closed_at IS NULL)
            OR
            (status = 'CLOSED' AND closed_at IS NOT NULL)
        )
);

CREATE INDEX idx_agent_conversations_status_updated_at
    ON agent_conversations (status, updated_at DESC);
