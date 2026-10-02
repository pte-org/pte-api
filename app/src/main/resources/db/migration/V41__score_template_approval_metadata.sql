ALTER TABLE score_templates
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(1000);
