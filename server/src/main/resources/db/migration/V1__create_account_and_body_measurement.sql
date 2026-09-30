CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_user_account_status CHECK (status IN ('ACTIVE', 'DELETION_REQUESTED')),
    CONSTRAINT ck_user_account_time_zone CHECK (CHAR_LENGTH(TRIM(time_zone)) > 0),
    CONSTRAINT ck_user_account_version CHECK (version >= 0)
);

CREATE TABLE external_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_external_identity_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT uq_external_identity_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uq_external_identity_user_provider UNIQUE (user_id, provider),
    CONSTRAINT ck_external_identity_provider CHECK (provider = 'GOOGLE'),
    CONSTRAINT ck_external_identity_subject CHECK (CHAR_LENGTH(TRIM(provider_subject)) > 0)
);

CREATE TABLE body_measurements (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    measured_on DATE NOT NULL,
    weight_kg NUMERIC(7, 3),
    waist_cm NUMERIC(6, 2),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_body_measurement_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT uq_body_measurement_user_day UNIQUE (user_id, measured_on),
    CONSTRAINT ck_body_measurement_present CHECK (weight_kg IS NOT NULL OR waist_cm IS NOT NULL),
    CONSTRAINT ck_body_measurement_weight CHECK (weight_kg IS NULL OR (weight_kg > 0 AND weight_kg <= 1000)),
    CONSTRAINT ck_body_measurement_waist CHECK (waist_cm IS NULL OR (waist_cm > 0 AND waist_cm <= 500)),
    CONSTRAINT ck_body_measurement_version CHECK (version >= 0)
);
