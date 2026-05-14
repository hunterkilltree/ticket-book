CREATE TABLE orders (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL,
    idempotency_key  VARCHAR(255) NOT NULL UNIQUE,
    total_amount     NUMERIC(10,2) NOT NULL,
    status           VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
