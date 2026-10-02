-- Persist the answer-count metric on assignments so Host load summaries can be aggregated
-- without deserializing every historical batch snapshot.
ALTER TABLE examiner_attempt_assignments
    ADD COLUMN eligible_answer_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_examiner_assignment_eligible_answer_count
        CHECK (eligible_answer_count >= 0);

UPDATE examiner_attempt_assignments assignment
SET eligible_answer_count = COALESCE((entry.value ->> 'eligibleAnswerCount')::INTEGER, 0)
FROM examiner_assignment_batches batch
CROSS JOIN LATERAL jsonb_array_elements(batch.assignment_snapshot_json::JSONB) AS entry(value)
WHERE assignment.batch_public_id = batch.public_id
  AND entry.value ->> 'attemptPublicId' = assignment.attempt_public_id::TEXT;

ALTER TABLE examiner_attempt_assignments
    ALTER COLUMN eligible_answer_count DROP DEFAULT;

CREATE INDEX idx_examiner_assignment_batch_history
    ON examiner_assignment_batches (tenant_id, session_public_id, created_at DESC, id DESC);
