CREATE TABLE subscriptions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                      UUID NOT NULL REFERENCES organizations(id),
    plan_id                     UUID NOT NULL REFERENCES plans(id),
    stripe_customer_id          VARCHAR(255),
    stripe_subscription_id      VARCHAR(255) UNIQUE,
    stripe_subscription_item_id VARCHAR(255),
    status                      VARCHAR(50) NOT NULL,
    current_period_start        TIMESTAMP,
    current_period_end          TIMESTAMP,
    cancel_at_period_end        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_org_id
    ON subscriptions(org_id);

CREATE INDEX idx_subscriptions_stripe_subscription_id
    ON subscriptions(stripe_subscription_id);

CREATE UNIQUE INDEX ux_subscriptions_one_open_per_org
    ON subscriptions(org_id)
    WHERE status <> 'CANCELLED';