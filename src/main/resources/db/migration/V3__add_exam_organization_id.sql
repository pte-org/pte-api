-- Schema reference: add organization_id to exam for grader scope filtering
-- Pre-migration check: SELECT COUNT(*) FROM exam WHERE host_id IS NOT NULL
--   AND NOT EXISTS (SELECT 1 FROM host WHERE id = exam.host_id);
-- → If > 0, orphaned exams exist; backfill will leave NULL organization_id for those rows.

ALTER TABLE exam ADD COLUMN IF NOT EXISTS organization_id BIGINT;

-- Backfill from host.organization_id
UPDATE exam e
SET    organization_id = (SELECT h.organization_id FROM host h WHERE h.id = e.host_id)
WHERE  e.host_id IS NOT NULL
AND    e.organization_id IS NULL;

-- Post-migration assertion: SELECT COUNT(*) FROM exam WHERE organization_id IS NULL AND host_id IS NOT NULL;
-- Must return 0.

CREATE INDEX IF NOT EXISTS idx_exam_organization_id ON exam(organization_id);
