ALTER TABLE organizations
    ADD COLUMN province_code VARCHAR(16),
    ADD COLUMN province_name VARCHAR(255),
    ADD COLUMN ward_code VARCHAR(16),
    ADD COLUMN ward_name VARCHAR(255);
