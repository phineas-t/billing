CREATE TABLE stripe_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stripe_event_id     VARCHAR(255) NOT NULL UNIQUE,
    event_type          VARCHAR(100) NOT NULL,
    status              VARCHAR(50)  NOT NULL DEFAULT 'PROCESSED',
    processing_attempts INTEGER      NOT NULL DEFAULT 1,
    last_error          TEXT,
    received_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMP
);

CREATE INDEX idx_stripe_events_stripe_event_id
    ON stripe_events(stripe_event_id);

CREATE INDEX idx_stripe_events_status
    ON stripe_events(status);