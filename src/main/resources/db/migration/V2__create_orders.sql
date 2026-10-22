CREATE TABLE orders (

    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_orders_customer
        FOREIGN KEY (customer_id)
        REFERENCES customer(id),

    CONSTRAINT chk_orders_total_non_negative
        CHECK (total_amount >= 0)
);

CREATE INDEX idx_orders_customer_id
    ON orders(customer_id);
