CREATE TABLE meal_reviews (
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    source_week DATE NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0),
    payload TEXT NOT NULL,
    PRIMARY KEY (user_id,source_week)
);
CREATE TABLE meal_review_events (
    user_id UUID NOT NULL,
    source_week DATE NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0),
    payload TEXT NOT NULL,
    PRIMARY KEY (user_id,source_week,version),
    FOREIGN KEY (user_id,source_week) REFERENCES meal_reviews(user_id,source_week) ON DELETE CASCADE
);
CREATE TABLE meal_day_plans (
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    meal_date DATE NOT NULL,
    slot_id UUID NOT NULL,
    payload TEXT NOT NULL,
    PRIMARY KEY (user_id,meal_date,slot_id)
);
