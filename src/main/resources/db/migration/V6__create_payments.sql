CREATE TABLE payments (

    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    amount NUMERIC(19, 4) NOT NULL,
    amount_refunded NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL,
    provider_reference VARCHAR(150) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_payments_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id),

    CONSTRAINT chk_payments_amount_positive
        CHECK (amount > 0),

    CONSTRAINT chk_payments_amount_refunded
        CHECK (
            amount_refunded >= 0
            AND amount_refunded <= amount
        ),

    CONSTRAINT chk_payments_status
        CHECK (
            status IN (
                'CAPTURED',
                'PARTIALLY_REFUNDED',
                'REFUNDED'
            )
        ),

    CONSTRAINT chk_payments_currency
        CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_payments_status
    ON payments(status);

INSERT INTO payments (
    order_id,
    amount,
    amount_refunded,
    currency,
    provider_reference,
    status,
    captured_at,
    created_at
)
VALUES (
    1,
    720.00,
    0.00,
    'EUR',
    'DEMO-PAYMENT-ORDER-1',
    'CAPTURED',
    TIMESTAMPTZ '2026-10-20 09:45:00+00',
    TIMESTAMPTZ '2026-10-20 09:45:00+00'
);
