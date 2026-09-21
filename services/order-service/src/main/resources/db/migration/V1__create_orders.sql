CREATE TABLE orders (
    id              uuid          PRIMARY KEY,
    idempotency_key varchar(255)  NOT NULL,
    request_hash    char(64)      NOT NULL,
    customer_id     varchar(100)  NOT NULL,
    currency        char(3)       NOT NULL,
    status          varchar(20)   NOT NULL,
    total_amount    numeric(19,4) NOT NULL,
    created_at      timestamptz   NOT NULL,
    CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE order_items (
    order_id   uuid          NOT NULL REFERENCES orders (id),
    line_no    int           NOT NULL,
    sku        varchar(100)  NOT NULL,
    quantity   int           NOT NULL CHECK (quantity > 0),
    unit_price numeric(19,4) NOT NULL CHECK (unit_price >= 0),
    PRIMARY KEY (order_id, line_no)
);
