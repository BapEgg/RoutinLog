CREATE TABLE program_applications (
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    request_payload TEXT NOT NULL,
    result_payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, request_id)
);
CREATE TABLE workout_preparation (
    user_id UUID PRIMARY KEY REFERENCES user_accounts(id) ON DELETE CASCADE,
    version BIGINT NOT NULL CHECK (version >= 0),
    payload TEXT NOT NULL
);
