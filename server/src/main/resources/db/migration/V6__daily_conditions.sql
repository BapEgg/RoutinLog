CREATE TABLE daily_conditions (
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    recorded_on DATE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    payload TEXT NOT NULL,
    PRIMARY KEY (user_id, recorded_on)
);
