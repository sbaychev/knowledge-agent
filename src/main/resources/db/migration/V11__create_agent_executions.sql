CREATE TABLE agent_executions
(
    id                  UUID PRIMARY KEY,
    conversation_id     UUID NOT NULL,
    status              VARCHAR(16) NOT NULL,
    correlation_id      VARCHAR(64),
    started_at          TIMESTAMPTZ NOT NULL,
    completed_at        TIMESTAMPTZ,
    duration_ms         BIGINT,
    failure_code        VARCHAR(64),
    last_event_sequence INTEGER NOT NULL DEFAULT 0,
    version             BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_agent_executions_conversation
        FOREIGN KEY (conversation_id)
            REFERENCES agent_conversations (id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_agent_executions_status
        CHECK (status IN (
            'RUNNING',
            'SUCCEEDED',
            'FAILED',
            'TIMED_OUT',
            'REJECTED'
        )),

    CONSTRAINT chk_agent_executions_lifecycle
        CHECK (
            (
                status = 'RUNNING'
                AND completed_at IS NULL
                AND duration_ms IS NULL
                AND failure_code IS NULL
            )
            OR
            (
                status = 'SUCCEEDED'
                AND completed_at IS NOT NULL
                AND duration_ms IS NOT NULL
                AND failure_code IS NULL
            )
            OR
            (
                status IN ('FAILED', 'TIMED_OUT', 'REJECTED')
                AND completed_at IS NOT NULL
                AND duration_ms IS NOT NULL
                AND failure_code IS NOT NULL
            )
        ),

    CONSTRAINT chk_agent_executions_duration
        CHECK (duration_ms IS NULL OR duration_ms >= 0),

    CONSTRAINT chk_agent_executions_event_sequence
        CHECK (last_event_sequence >= 0)
);

CREATE INDEX idx_agent_executions_conversation_started
    ON agent_executions (conversation_id, started_at DESC);

CREATE INDEX idx_agent_executions_status_started
    ON agent_executions (status, started_at DESC);


CREATE TABLE agent_execution_events
(
    id             BIGSERIAL PRIMARY KEY,
    execution_id   UUID NOT NULL,
    sequence_number INTEGER NOT NULL,
    event_type     VARCHAR(32) NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payload_version INTEGER NOT NULL DEFAULT 1,
    payload_json   JSONB NOT NULL DEFAULT '{}'::jsonb,

    CONSTRAINT fk_agent_execution_events_execution
        FOREIGN KEY (execution_id)
            REFERENCES agent_executions (id)
            ON DELETE CASCADE,

    CONSTRAINT uk_agent_execution_event_sequence
        UNIQUE (execution_id, sequence_number),

    CONSTRAINT chk_agent_execution_event_sequence
        CHECK (sequence_number > 0),

    CONSTRAINT chk_agent_execution_event_payload_version
        CHECK (payload_version > 0),

    CONSTRAINT chk_agent_execution_event_type
        CHECK (event_type IN (
            'EXECUTION_STARTED',
            'EXECUTION_SUCCEEDED',
            'EXECUTION_FAILED',
            'EXECUTION_TIMED_OUT',
            'EXECUTION_REJECTED'
        )),

    CONSTRAINT chk_agent_execution_event_payload
        CHECK (jsonb_typeof(payload_json) = 'object')
);

CREATE INDEX idx_agent_execution_events_execution_sequence
    ON agent_execution_events (execution_id, sequence_number);
