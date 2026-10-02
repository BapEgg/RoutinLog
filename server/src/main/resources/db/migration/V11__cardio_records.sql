CREATE TABLE cardio_records (
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    id UUID NOT NULL,
    recorded_on DATE NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0),
    payload TEXT NOT NULL,
    PRIMARY KEY (user_id, id)
);
CREATE INDEX cardio_records_by_date ON cardio_records(user_id, recorded_on);
