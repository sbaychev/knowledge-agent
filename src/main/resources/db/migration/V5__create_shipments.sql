CREATE TABLE shipments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    carrier VARCHAR(100) NOT NULL,
    tracking_number VARCHAR(150) NOT NULL,
    status VARCHAR(30) NOT NULL,
    shipped_at TIMESTAMP WITH TIME ZONE NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_shipments_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id),

    CONSTRAINT chk_shipments_status
        CHECK (status IN ('IN_TRANSIT', 'DELIVERED'))
);

CREATE INDEX idx_shipments_tracking_number
    ON shipments(tracking_number);

INSERT INTO shipments (
    order_id,
    carrier,
    tracking_number,
    status,
    shipped_at,
    delivered_at,
    created_at
)
VALUES (
    1,
    'DHL',
    'DHL-DEMO-ORDER-1',
    'IN_TRANSIT',
    TIMESTAMPTZ '2026-10-20 10:30:00+00',
    NULL,
    TIMESTAMPTZ '2026-10-20 10:30:00+00'
);
