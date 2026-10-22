INSERT INTO customer (
    id,
    external_id,
    name,
    email,
    status,
    created_at
)
VALUES
    (
        1,
        'CUST-SEED-001',
        'Seed Customer 1',
        'seed.customer1@example.test',
        'ACTIVE',
        CURRENT_TIMESTAMP
    ),
    (
        2,
        'CUST-SEED-002',
        'Seed Customer 2',
        'seed.customer2@example.test',
        'ACTIVE',
        CURRENT_TIMESTAMP
    );

SELECT setval(
    pg_get_serial_sequence('customer', 'id'),
    (SELECT MAX(id) FROM customer),
    true
);

INSERT INTO orders (
    customer_id,
    status,
    total_amount,
    currency,
    created_at
)
VALUES (
    1,
    'SHIPPED',
    720.00,
    'EUR',
    NOW()
);

INSERT INTO orders (
    customer_id,
    status,
    total_amount,
    currency,
    created_at
)
VALUES (
    1,
    'DELIVERED',
    120.00,
    'EUR',
    NOW()
);

INSERT INTO orders (
    customer_id,
    status,
    total_amount,
    currency,
    created_at
)
VALUES (
    2,
    'PROCESSING',
    49.00,
    'EUR',
    NOW()
);

INSERT INTO orders (
    customer_id,
    status,
    total_amount,
    currency,
    created_at
)
SELECT
    id,
    'SHIPPED',
    720.00,
    'EUR',
    NOW()
FROM customer
WHERE external_id = 'C-1001';


-- Order 1: total EUR 720.00
INSERT INTO order_items (
    order_id, product_id, product_name, quantity, unit_price
)
VALUES
    (1, 'SKU-LAPTOP-001', 'Laptop', 1, 650.00),
    (1, 'SKU-DOCK-001', 'USB-C docking station', 1, 70.00);

-- Order 2: total EUR 120.00
INSERT INTO order_items (
    order_id, product_id, product_name, quantity, unit_price
)
VALUES
    (2, 'SKU-HEADSET-001', 'Wireless headset', 1, 120.00);

-- Order 3: total EUR 49.00
INSERT INTO order_items (
    order_id, product_id, product_name, quantity, unit_price
)
VALUES
    (3, 'SKU-HUB-001', 'USB-C hub', 1, 49.00);
