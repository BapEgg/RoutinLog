CREATE TABLE workout_exercises (
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    equipment VARCHAR(80) NOT NULL,
    target VARCHAR(80) NOT NULL,
    record_type VARCHAR(20) NOT NULL CHECK (record_type IN ('WEIGHT_REPS','REPS','DURATION')),
    load_convention VARCHAR(20) NOT NULL CHECK (load_convention IN ('TOTAL','PER_HAND','MACHINE','EXTERNAL','BODYWEIGHT','UNSPECIFIED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (user_id,id)
);
CREATE TABLE workout_routines (
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    payload TEXT NOT NULL,
    PRIMARY KEY (user_id,id)
);
-- Current references protect ordinary deletes in the service. Cascade supports account deletion.
CREATE TABLE workout_routine_exercises (
    user_id UUID NOT NULL,
    routine_id UUID NOT NULL,
    exercise_id UUID NOT NULL,
    PRIMARY KEY (user_id,routine_id,exercise_id),
    FOREIGN KEY (user_id,routine_id) REFERENCES workout_routines(user_id,id) ON DELETE CASCADE,
    FOREIGN KEY (user_id,exercise_id) REFERENCES workout_exercises(user_id,id) ON DELETE CASCADE
);
CREATE INDEX workout_routine_exercise_lookup ON workout_routine_exercises(user_id,exercise_id);
-- Revisions and historical snapshots have no foreign keys to current exercise/routine definitions.
CREATE TABLE workout_plan_revisions (
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    version BIGINT NOT NULL CHECK (version >= 0),
    effective_from DATE NOT NULL,
    payload TEXT NOT NULL,
    PRIMARY KEY (user_id,version)
);
CREATE INDEX workout_plan_date ON workout_plan_revisions(user_id,effective_from,version);
CREATE TABLE workout_overrides (
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    workout_date DATE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    payload TEXT NOT NULL,
    PRIMARY KEY (user_id,workout_date)
);
CREATE TABLE workout_sessions (
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    id UUID NOT NULL,
    workout_date DATE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    payload TEXT NOT NULL,
    PRIMARY KEY (user_id,id),
    UNIQUE (user_id,workout_date)
);
