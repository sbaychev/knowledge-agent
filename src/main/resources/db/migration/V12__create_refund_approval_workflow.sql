CREATE TABLE refund_approval_requests
(
    id                  UUID PRIMARY KEY,
    payment_id          BIGINT NOT NULL,
    execution_id        UUID,
    requester_subject   VARCHAR(128) NOT NULL,
    approver_subject    VARCHAR(128),
    idempotency_key     VARCHAR(100) NOT NULL,
    amount              NUMERIC(19, 4) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    reason              VARCHAR(500) NOT NULL,
    status              VARCHAR(16) NOT NULL,
    rejection_reason    VARCHAR(500),
    refund_id           BIGINT,
    created_at          TIMESTAMPTZ NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    decided_at          TIMESTAMPTZ,
    submitted_at        TIMESTAMPTZ,
    last_event_sequence INTEGER NOT NULL DEFAULT 0,
    version             BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_refund_approval_payment
        FOREIGN KEY (payment_id)
            REFERENCES payments (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_refund_approval_execution
        FOREIGN KEY (execution_id)
            REFERENCES agent_executions (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_refund_approval_refund
        FOREIGN KEY (refund_id)
            REFERENCES refunds (id)
            ON DELETE RESTRICT,

    CONSTRAINT uk_refund_approval_requester_idempotency
        UNIQUE (requester_subject, idempotency_key),

    CONSTRAINT uk_refund_approval_refund
        UNIQUE (refund_id),

    CONSTRAINT chk_refund_approval_amount
        CHECK (amount > 0),

    CONSTRAINT chk_refund_approval_currency
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT chk_refund_approval_status
        CHECK (status IN ('PENDING', 'SUBMITTED', 'REJECTED', 'EXPIRED')),

    CONSTRAINT chk_refund_approval_timestamps
        CHECK (expires_at > created_at),

    CONSTRAINT chk_refund_approval_event_sequence
        CHECK (last_event_sequence >= 0),

    CONSTRAINT chk_refund_approval_lifecycle
        CHECK (
            (
                status = 'PENDING'
                AND approver_subject IS NULL
                AND rejection_reason IS NULL
                AND refund_id IS NULL
                AND decided_at IS NULL
                AND submitted_at IS NULL
            )
            OR
            (
                status = 'SUBMITTED'
                AND approver_subject IS NOT NULL
                AND rejection_reason IS NULL
                AND refund_id IS NOT NULL
                AND decided_at IS NOT NULL
                AND submitted_at IS NOT NULL
            )
            OR
            (
                status = 'REJECTED'
                AND approver_subject IS NOT NULL
                AND rejection_reason IS NOT NULL
                AND refund_id IS NULL
                AND decided_at IS NOT NULL
                AND submitted_at IS NULL
            )
            OR
            (
                status = 'EXPIRED'
                AND approver_subject IS NULL
                AND rejection_reason IS NULL
                AND refund_id IS NULL
                AND decided_at IS NOT NULL
                AND submitted_at IS NULL
            )
        )
);

CREATE INDEX idx_refund_approval_requests_pending_expiry
    ON refund_approval_requests (status, expires_at, created_at);

CREATE INDEX idx_refund_approval_requests_payment_created
    ON refund_approval_requests (payment_id, created_at DESC);


CREATE TABLE refund_approval_events
(
    id                  BIGSERIAL PRIMARY KEY,
    approval_request_id UUID NOT NULL,
    sequence_number     INTEGER NOT NULL,
    event_type          VARCHAR(40) NOT NULL,
    actor_subject       VARCHAR(128),
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payload_version     INTEGER NOT NULL DEFAULT 1,
    payload_json        JSONB NOT NULL DEFAULT '{}'::jsonb,

    CONSTRAINT fk_refund_approval_events_request
        FOREIGN KEY (approval_request_id)
            REFERENCES refund_approval_requests (id)
            ON DELETE RESTRICT,

    CONSTRAINT uk_refund_approval_event_sequence
        UNIQUE (approval_request_id, sequence_number),

    CONSTRAINT chk_refund_approval_event_sequence
        CHECK (sequence_number > 0),

    CONSTRAINT chk_refund_approval_event_payload_version
        CHECK (payload_version > 0),

    CONSTRAINT chk_refund_approval_event_type
        CHECK (event_type IN (
            'REFUND_APPROVAL_REQUESTED',
            'REFUND_APPROVAL_APPROVED',
            'REFUND_APPROVAL_REJECTED',
            'REFUND_APPROVAL_EXPIRED',
            'REFUND_SUBMITTED'
        )),

    CONSTRAINT chk_refund_approval_event_payload
        CHECK (jsonb_typeof(payload_json) = 'object')
);

CREATE FUNCTION reject_refund_approval_request_identity_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $approval_identity$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.payment_id IS DISTINCT FROM OLD.payment_id
       OR NEW.execution_id IS DISTINCT FROM OLD.execution_id
       OR NEW.requester_subject IS DISTINCT FROM OLD.requester_subject
       OR NEW.idempotency_key IS DISTINCT FROM OLD.idempotency_key
       OR NEW.amount IS DISTINCT FROM OLD.amount
       OR NEW.currency IS DISTINCT FROM OLD.currency
       OR NEW.reason IS DISTINCT FROM OLD.reason
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR NEW.expires_at IS DISTINCT FROM OLD.expires_at
    THEN
        RAISE EXCEPTION 'refund_approval_requests identity fields are immutable';
    END IF;

    RETURN NEW;
END;
$approval_identity$;

CREATE TRIGGER trg_refund_approval_requests_identity_immutable
    BEFORE UPDATE ON refund_approval_requests
    FOR EACH ROW
    EXECUTE FUNCTION reject_refund_approval_request_identity_mutation();

CREATE FUNCTION reject_refund_approval_event_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'refund_approval_events is append-only';
END;
$$;

CREATE TRIGGER trg_refund_approval_events_append_only
    BEFORE UPDATE OR DELETE ON refund_approval_events
    FOR EACH ROW
    EXECUTE FUNCTION reject_refund_approval_event_mutation();

-- The unique constraint on agent_execution_events already creates
-- an index over (execution_id, sequence_number).
DROP INDEX IF EXISTS idx_agent_execution_events_execution_sequence;
