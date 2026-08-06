CREATE TABLE plans (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(50)  NOT NULL UNIQUE,
    display_name        VARCHAR(100) NOT NULL,
    stripe_price_id     VARCHAR(255),
    monthly_price_cents INTEGER      NOT NULL DEFAULT 0,
    billing_interval    VARCHAR(20)  NOT NULL DEFAULT 'MONTHLY',
    limits              JSONB        NOT NULL DEFAULT '{}',
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_plans_code ON plans(code);
CREATE INDEX idx_plans_active ON plans(active);