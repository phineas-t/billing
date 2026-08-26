CREATE TABLE usage_records (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id              UUID NOT NULL REFERENCES organizations(id),
    subscription_id     UUID REFERENCES subscriptions(id),
    idempotency_key     VARCHAR(255) NOT NULL UNIQUE,
    metric              VARCHAR(100) NOT NULL DEFAULT 'api_calls',
    quantity            INTEGER NOT NULL DEFAULT 1,
    recorded_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    billing_period      VARCHAR(20) NOT NULL
);

CREATE INDEX idx_usage_records_org_id
    ON usage_records(org_id);
CREATE INDEX idx_usage_records_billing_period
    ON usage_records(org_id, billing_period);
CREATE INDEX idx_usage_records_idempotency_key
    ON usage_records(idempotency_key);