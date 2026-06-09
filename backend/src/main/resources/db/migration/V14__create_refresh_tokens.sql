CREATE TABLE refresh_tokens (
    id            BIGSERIAL    PRIMARY KEY,
    token_hash    VARCHAR(64)  NOT NULL UNIQUE,
    staff_user_id INTEGER      NOT NULL REFERENCES staff_user(id) ON DELETE CASCADE,
    expires_at    TIMESTAMP    NOT NULL,
    revoked       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_staff_user_id ON refresh_tokens(staff_user_id);
