CREATE TABLE payments (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                 UUID         NOT NULL UNIQUE,
    idempotency_key          VARCHAR(255) NOT NULL UNIQUE,
    amount                   NUMERIC(10,2) NOT NULL,
    status                   VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    gateway_transaction_id   VARCHAR(255),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);
