CREATE TABLE venues (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(255) NOT NULL,
    address        VARCHAR(500) NOT NULL,
    total_capacity INT          NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(255) NOT NULL,
    artist      VARCHAR(255) NOT NULL,
    event_date  TIMESTAMPTZ  NOT NULL,
    venue_id    UUID         NOT NULL REFERENCES venues(id),
    status      VARCHAR(50)  NOT NULL DEFAULT 'DRAFT',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
