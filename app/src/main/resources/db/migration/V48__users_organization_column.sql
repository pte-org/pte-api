-- 1 username = 1 organization, step 1 (expand): additive only. Nothing reads the column until V49 fills it.
ALTER TABLE users ADD COLUMN organization_id uuid;

CREATE INDEX idx_users_organization ON users (organization_id);

-- Mapping written by V49 for every account it creates when it splits a person who belonged to several
-- organizations. Export it before V51 (the contract step drops it): it is how people are told their new username.
CREATE TABLE identity_account_split_log (
    id bigserial PRIMARY KEY,
    original_user_id bigint NOT NULL,
    original_public_id uuid NOT NULL,
    new_user_id bigint NOT NULL,
    new_public_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    old_username character varying(255) NOT NULL,
    new_username character varying(255) NOT NULL,
    email character varying(255),
    logged_at timestamp(6) with time zone NOT NULL DEFAULT now()
);
