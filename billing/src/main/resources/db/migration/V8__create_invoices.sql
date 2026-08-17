CREATE TABLE invoices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id              UUID NOT NULL REFERENCES organizations(id),
    subscription_id     UUID REFERENCES subscriptions(id),
    stripe_invoice_id   VARCHAR(255) NOT NULL UNIQUE,
    amount_due_cents    INTEGER      NOT NULL DEFAULT 0,
    amount_paid_cents   INTEGER      NOT NULL DEFAULT 0,
    currency            VARCHAR(10)  NOT NULL DEFAULT 'usd',
    status              VARCHAR(50)  NOT NULL,
    invoice_pdf_url     VARCHAR(500),
    hosted_invoice_url  VARCHAR(500),
    period_start        TIMESTAMP,
    period_end          TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoices_org_id
    ON invoices(org_id);

CREATE INDEX idx_invoices_stripe_invoice_id
    ON invoices(stripe_invoice_id);

CREATE INDEX idx_invoices_subscription_id
    ON invoices(subscription_id);