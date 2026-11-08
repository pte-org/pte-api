-- Tokens spent by AI question generation, for the admin AI overview. One row per Vietnam day, role (GENERATOR or
-- REVIEWER) and model; numbers only, never a prompt or an answer. A call that failed before the provider reported its
-- usage is not counted.
CREATE TABLE generation_usage_daily (
    usage_date DATE NOT NULL,
    role VARCHAR(16) NOT NULL,
    model VARCHAR(64) NOT NULL,
    calls INTEGER NOT NULL DEFAULT 0 CHECK (calls >= 0),
    tokens_in BIGINT NOT NULL DEFAULT 0 CHECK (tokens_in >= 0),
    tokens_out BIGINT NOT NULL DEFAULT 0 CHECK (tokens_out >= 0),
    PRIMARY KEY (usage_date, role, model)
);
