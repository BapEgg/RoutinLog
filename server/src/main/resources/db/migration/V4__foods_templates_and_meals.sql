CREATE TABLE foods (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    brand VARCHAR(80),
    basis_grams NUMERIC(8,2) NOT NULL CHECK (basis_grams > 0 AND basis_grams <= 100000),
    kcal NUMERIC(8,2) CHECK (kcal BETWEEN 0 AND 100000),
    carbs_g NUMERIC(8,2) CHECK (carbs_g BETWEEN 0 AND 100000),
    protein_g NUMERIC(8,2) CHECK (protein_g BETWEEN 0 AND 100000),
    fat_g NUMERIC(8,2) CHECK (fat_g BETWEEN 0 AND 100000),
    fiber_g NUMERIC(8,2) CHECK (fiber_g BETWEEN 0 AND 100000),
    preparation VARCHAR(16) NOT NULL CHECK (preparation IN ('RAW','COOKED','AS_SOLD','UNKNOWN')),
    source_note VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_food_owner_id UNIQUE (user_id,id),
    CONSTRAINT ck_food_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
);
CREATE INDEX ix_food_owner_name ON foods(user_id,name);

CREATE TABLE meal_templates (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    memo VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_template_owner_id UNIQUE(user_id,id)
);
CREATE INDEX ix_template_owner_name ON meal_templates(user_id,name);
CREATE TABLE meal_template_items (
    user_id UUID NOT NULL,
    template_id UUID NOT NULL,
    food_id UUID NOT NULL,
    position INTEGER NOT NULL CHECK (position BETWEEN 0 AND 49),
    grams NUMERIC(8,2) NOT NULL CHECK (grams > 0 AND grams <= 100000),
    PRIMARY KEY(template_id,food_id),
    CONSTRAINT uq_template_position UNIQUE(template_id,position),
    CONSTRAINT fk_template_item_owner FOREIGN KEY(user_id,template_id) REFERENCES meal_templates(user_id,id) ON DELETE CASCADE,
    -- Ordinary food deletion is blocked by MealService while referenced. Cascades allow whole-account erasure regardless of FK traversal order.
    CONSTRAINT fk_template_item_food_owner FOREIGN KEY(user_id,food_id) REFERENCES foods(user_id,id) ON DELETE CASCADE
);

CREATE TABLE meal_plans (
    user_id UUID PRIMARY KEY REFERENCES user_accounts(id) ON DELETE CASCADE,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE meal_plan_slots (
    user_id UUID NOT NULL REFERENCES meal_plans(user_id) ON DELETE CASCADE,
    id UUID NOT NULL,
    label VARCHAR(80) NOT NULL CHECK (CHAR_LENGTH(TRIM(label)) > 0),
    template_id UUID,
    position INTEGER NOT NULL CHECK (position BETWEEN 0 AND 9),
    PRIMARY KEY(user_id,id),
    CONSTRAINT uq_plan_slot_position UNIQUE(user_id,position),
    CONSTRAINT uq_plan_slot_label UNIQUE(user_id,label),
    -- Ordinary template deletion requires unlinking the plan first; whole-account erasure must still cascade.
    CONSTRAINT fk_plan_template_owner FOREIGN KEY(user_id,template_id) REFERENCES meal_templates(user_id,id) ON DELETE CASCADE
);

CREATE TABLE meal_records (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    meal_date DATE NOT NULL,
    slot_id UUID NOT NULL,
    slot_label VARCHAR(80) NOT NULL CHECK (CHAR_LENGTH(TRIM(slot_label)) > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('EATEN','SKIPPED')),
    note VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_meal_owner_id UNIQUE(user_id,id),
    CONSTRAINT uq_meal_owner_day_slot UNIQUE(user_id,meal_date,slot_id)
);
CREATE INDEX ix_meal_owner_date ON meal_records(user_id,meal_date);
CREATE TABLE meal_record_items (
    user_id UUID NOT NULL,
    meal_id UUID NOT NULL,
    id UUID NOT NULL,
    position INTEGER NOT NULL CHECK (position BETWEEN 0 AND 49),
    -- Deliberately no FK to foods: this is an immutable historical snapshot.
    food_id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    brand VARCHAR(80),
    basis_grams NUMERIC(8,2) NOT NULL CHECK (basis_grams > 0 AND basis_grams <= 100000),
    kcal NUMERIC(8,2) CHECK (kcal BETWEEN 0 AND 100000),
    carbs_g NUMERIC(8,2) CHECK (carbs_g BETWEEN 0 AND 100000),
    protein_g NUMERIC(8,2) CHECK (protein_g BETWEEN 0 AND 100000),
    fat_g NUMERIC(8,2) CHECK (fat_g BETWEEN 0 AND 100000),
    fiber_g NUMERIC(8,2) CHECK (fiber_g BETWEEN 0 AND 100000),
    preparation VARCHAR(16) NOT NULL CHECK (preparation IN ('RAW','COOKED','AS_SOLD','UNKNOWN')),
    source_note VARCHAR(500),
    grams NUMERIC(8,2) CHECK (grams > 0 AND grams <= 100000),
    PRIMARY KEY(meal_id,id),
    CONSTRAINT uq_meal_item_position UNIQUE(meal_id,position),
    CONSTRAINT fk_meal_item_owner FOREIGN KEY(user_id,meal_id) REFERENCES meal_records(user_id,id) ON DELETE CASCADE
);
