CREATE TABLE food_thumbnails (
    user_id UUID NOT NULL,
    resource_id UUID NOT NULL,
    version BIGINT NOT NULL CHECK(version >= 0),
    jpeg BYTEA NOT NULL,
    PRIMARY KEY(user_id,resource_id),
    FOREIGN KEY(user_id,resource_id) REFERENCES foods(user_id,id) ON DELETE CASCADE
);
CREATE TABLE exercise_thumbnails (
    user_id UUID NOT NULL,
    resource_id UUID NOT NULL,
    version BIGINT NOT NULL CHECK(version >= 0),
    jpeg BYTEA NOT NULL,
    PRIMARY KEY(user_id,resource_id),
    FOREIGN KEY(user_id,resource_id) REFERENCES workout_exercises(user_id,id) ON DELETE CASCADE
);
