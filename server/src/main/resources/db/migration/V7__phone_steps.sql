CREATE TABLE step_connections (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    time_zone VARCHAR(80) NOT NULL
);
CREATE INDEX step_connection_owner ON step_connections(user_id);
CREATE TABLE step_observations (
    connection_id UUID NOT NULL REFERENCES step_connections(id) ON DELETE CASCADE,
    recorded_on DATE NOT NULL,
    from_time TIMESTAMP WITH TIME ZONE NOT NULL,
    through_time TIMESTAMP WITH TIME ZONE NOT NULL,
    steps BIGINT NOT NULL CHECK (steps >= 0 AND steps <= 300000),
    PRIMARY KEY (connection_id, recorded_on),
    CHECK (through_time > from_time)
);
