-- Write Email is not an exam task type and is gone from practice; close any unfinished session still holding it.
-- Saved payloads and audit rows are kept. Plain UPDATEs keep this portable to the H2 context tests.
UPDATE practice_sessions
SET status = 'DISCARDED',
    discarded_at = COALESCE(discarded_at, CURRENT_TIMESTAMP)
WHERE deleted = FALSE
  AND status IN ('OVERVIEW', 'IN_PROGRESS')
  AND id IN (SELECT practice_session_id FROM practice_session_items
             WHERE deleted = FALSE AND status = 'PENDING' AND task_code = 'WRITE_EMAIL');

UPDATE practice_session_items
SET status = 'SKIPPED',
    skipped_at = COALESCE(skipped_at, CURRENT_TIMESTAMP)
WHERE deleted = FALSE
  AND status = 'PENDING'
  AND practice_session_id IN (SELECT id FROM practice_sessions WHERE status = 'DISCARDED');

-- Practice answers are scored by the exam scorers (V130), so the rubric catalog and the AI explanation feature are gone.
-- Destructive: drops the explanation and rubric tables and the per-item columns that only they used.
-- objective_score, objective_evaluated_at and objective_options_json stay: they hold the objective result.

DROP TABLE IF EXISTS practice_explanation_outbox;
DROP TABLE IF EXISTS practice_explanation_jobs;
DROP TABLE IF EXISTS practice_explanations;

DROP TABLE IF EXISTS rubric_criterion_levels;
DROP TABLE IF EXISTS rubric_criteria;
DROP TABLE IF EXISTS rubric_revisions;

DROP INDEX IF EXISTS idx_practice_session_items_rubric;

ALTER TABLE practice_session_items DROP COLUMN IF EXISTS rubric_revision_public_id;
ALTER TABLE practice_session_items DROP COLUMN IF EXISTS rubric_revision_number;
ALTER TABLE practice_session_items DROP COLUMN IF EXISTS rubric_canonical_hash;
ALTER TABLE practice_session_items DROP COLUMN IF EXISTS rubric_snapshot_json;
ALTER TABLE practice_session_items DROP COLUMN IF EXISTS runtime_compatibility_hash;

ALTER TABLE practice_session_items DROP COLUMN IF EXISTS objective_evaluator_version;
ALTER TABLE practice_session_items DROP COLUMN IF EXISTS objective_result_hash;
ALTER TABLE practice_session_items DROP COLUMN IF EXISTS objective_evidence_identity_hash;

ALTER TABLE practice_session_items DROP COLUMN IF EXISTS form_word_count;
ALTER TABLE practice_session_items DROP COLUMN IF EXISTS form_status;
ALTER TABLE practice_session_items DROP COLUMN IF EXISTS form_evaluator_version;
ALTER TABLE practice_session_items DROP COLUMN IF EXISTS form_result_hash;
ALTER TABLE practice_session_items DROP COLUMN IF EXISTS form_evidence_identity_hash;
