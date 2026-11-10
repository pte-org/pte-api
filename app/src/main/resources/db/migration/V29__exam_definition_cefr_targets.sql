-- Share of each CEFR level an exam built from the template should aim for, as whole percents summing to 100.
-- A template without rows draws questions without regard to their level.
CREATE TABLE exam_definition_cefr_targets (
    exam_definition_id bigint NOT NULL REFERENCES exam_definitions (id),
    level character varying(2) NOT NULL,
    percent integer NOT NULL,
    PRIMARY KEY (exam_definition_id, level),
    CONSTRAINT exam_definition_cefr_targets_level_check CHECK (level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    CONSTRAINT exam_definition_cefr_targets_percent_check CHECK (percent > 0 AND percent <= 100)
);

-- The standard PTE Academic range is B1 to C2; Pearson publishes no percentages, so the platform templates start
-- with an even split.
INSERT INTO exam_definition_cefr_targets (exam_definition_id, level, percent)
SELECT d.id, levels.level, 25
FROM exam_definitions d
CROSS JOIN (VALUES ('B1'), ('B2'), ('C1'), ('C2')) AS levels(level)
WHERE d.tenant_id IS NULL AND d.status IN ('ACTIVE', 'DRAFT', 'PENDING_APPROVAL');
