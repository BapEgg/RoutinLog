CREATE TABLE auth_challenges (
    id UUID PRIMARY KEY,
    nonce_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT ck_auth_challenge_hash CHECK (CHAR_LENGTH(nonce_hash) = 64),
    CONSTRAINT ck_auth_challenge_expiry CHECK (expires_at > created_at)
);
CREATE INDEX ix_auth_challenge_expiry ON auth_challenges (expires_at);

CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    access_token_hash VARCHAR(64) NOT NULL UNIQUE,
    access_expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP(6) WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_auth_session_hash CHECK (CHAR_LENGTH(access_token_hash) = 64),
    CONSTRAINT ck_auth_session_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_auth_session_version CHECK (version >= 0)
);
CREATE INDEX ix_auth_session_user ON auth_sessions (user_id);
CREATE INDEX ix_auth_session_expiry ON auth_sessions (expires_at);

-- Keep spent hashes until the session expires so replay revokes the whole session.
CREATE TABLE auth_refresh_tokens (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT fk_auth_refresh_session FOREIGN KEY (session_id) REFERENCES auth_sessions (id) ON DELETE CASCADE,
    CONSTRAINT ck_auth_refresh_hash CHECK (CHAR_LENGTH(token_hash) = 64)
);
CREATE INDEX ix_auth_refresh_session ON auth_refresh_tokens (session_id);
