-- Admin License Code: a license code bound to one organization and redeemed in the same transaction.
-- Every column is nullable so legacy bearer codes keep their current meaning.
ALTER TABLE license_codes
    ADD COLUMN bound_tenant_id uuid,
    ADD COLUMN requested_starts_at timestamp(6) with time zone,
    ADD COLUMN contract_reference varchar(64),
    ADD COLUMN note varchar(500);

CREATE INDEX idx_license_codes_bound_tenant ON license_codes (bound_tenant_id);
