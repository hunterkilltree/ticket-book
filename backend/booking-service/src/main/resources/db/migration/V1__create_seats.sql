CREATE TABLE seats (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id   UUID         NOT NULL,
    section    VARCHAR(50)  NOT NULL,
    row        VARCHAR(10)  NOT NULL,
    number     VARCHAR(10)  NOT NULL,
    state      VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (event_id, section, row, number)
);

CREATE INDEX idx_seats_event_state ON seats(event_id, state);
