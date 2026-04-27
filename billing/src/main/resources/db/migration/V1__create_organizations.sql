CREATE EXTENSION IF NOT EXISTS "pgcrypto"; --enables random function for uuid auto-generation

CREATE TABLE organizations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    stripe_customer_id VARCHAR(255), --no "NOT NULL" since at the beginning no org has customer
    status          VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);