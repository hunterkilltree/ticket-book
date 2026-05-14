CREATE TABLE tickets (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id   UUID         NOT NULL,
    seat_id    UUID         NOT NULL,
    qr_code    VARCHAR(255) NOT NULL UNIQUE,
    status     VARCHAR(50)  NOT NULL DEFAULT 'ISSUED',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_tickets_order_id ON tickets(order_id);
