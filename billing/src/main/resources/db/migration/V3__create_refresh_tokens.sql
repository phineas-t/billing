CREATE TABLE refresh_tokens (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id),
    org_id                  UUID NOT NULL REFERENCES organizations(id),
    token_hash              VARCHAR(64) NOT NULL UNIQUE,
    expires_at              TIMESTAMP NOT NULL,
    revoked_at              TIMESTAMP NULL,
    replaced_by_token_hash  VARCHAR(64) NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_revoked_expires ON refresh_tokens(revoked_at,expires_at);