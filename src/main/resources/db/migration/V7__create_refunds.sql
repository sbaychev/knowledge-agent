CREATE TABLE refunds (

    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_refunds_payment
        FOREIGN KEY (payment_id)
        REFERENCES payments(id),

    CONSTRAINT uq_refunds_payment_idempotency
        UNIQUE (payment_id, idempotency_key),

    CONSTRAINT chk_refunds_amount_positive
        CHECK (amount > 0),

    CONSTRAINT chk_refunds_status
        CHECK (
            status IN ('REQUESTED', 'COMPLETED', 'FAILED')
        ),

    CONSTRAINT chk_refunds_timestamps
        CHECK (
            (
                status = 'REQUESTED'
                AND completed_at IS NULL
                AND failed_at IS NULL
            )
            OR
            (
                status = 'COMPLETED'
                AND completed_at IS NOT NULL
                AND failed_at IS NULL
            )
            OR
            (
                status = 'FAILED'
                AND completed_at IS NULL
                AND failed_at IS NOT NULL
            )
        ),

    CONSTRAINT chk_refunds_currency
        CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_refunds_payment_status
    ON refunds(payment_id, status);
