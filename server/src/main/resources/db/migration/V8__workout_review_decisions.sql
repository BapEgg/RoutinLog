CREATE TABLE workout_reviews (
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    source_week DATE NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0),
    payload TEXT NOT NULL,
    PRIMARY KEY (user_id,source_week)
);
-- Each accepted, held or regenerated draft is retained for later preference analysis.
CREATE TABLE workout_review_events (
    user_id UUID NOT NULL,
    source_week DATE NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0),
    payload TEXT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id,source_week,version),
    FOREIGN KEY (user_id,source_week) REFERENCES workout_reviews(user_id,source_week) ON DELETE CASCADE
);
