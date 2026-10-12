-- Add modern decision contracts, AI snapshot audit, and idempotent request receipts
-- to immutable ExaminerAnswerScore. Existing legacy rows remain safely representable.

ALTER TABLE examiner_answer_scores
    ADD COLUMN decision_kind VARCHAR(32),
    ADD COLUMN reviewed_ai_score INTEGER,
    ADD COLUMN reviewed_ai_version VARCHAR(64),
    ADD COLUMN expected_review_version VARCHAR(64),
    ADD COLUMN request_public_id UUID,
    ADD COLUMN request_fingerprint VARCHAR(128);

ALTER TABLE examiner_answer_scores
    ADD CONSTRAINT ck_examiner_score_decision_kind
        CHECK (decision_kind IS NULL OR decision_kind IN ('CONFIRM_AI', 'MANUAL', 'LEGACY_IMPORTED')),
    ADD CONSTRAINT ck_examiner_score_reviewed_ai_score
        CHECK (reviewed_ai_score IS NULL OR (reviewed_ai_score BETWEEN 0 AND 100)),
    ADD CONSTRAINT ck_examiner_score_confirm_ai_match
        CHECK (decision_kind IS DISTINCT FROM 'CONFIRM_AI'
            OR (reviewed_ai_score IS NOT NULL AND reviewed_ai_version IS NOT NULL AND score = reviewed_ai_score)),
    ADD CONSTRAINT ck_examiner_score_modern_metadata
        CHECK (
            -- Legacy row:
            (decision_kind IS NULL AND request_public_id IS NULL AND request_fingerprint IS NULL AND expected_review_version IS NULL)
            -- Modern row:
            OR (decision_kind IS NOT NULL AND request_public_id IS NOT NULL AND request_fingerprint IS NOT NULL AND expected_review_version IS NOT NULL)
            -- Bounded legacy imported:
            OR (decision_kind = 'LEGACY_IMPORTED')
        );

CREATE UNIQUE INDEX uq_examiner_score_receipt
    ON examiner_answer_scores (tenant_id, examiner_public_id, request_public_id)
    WHERE request_public_id IS NOT NULL;

CREATE INDEX idx_examiner_score_request
    ON examiner_answer_scores (tenant_id, request_public_id)
    WHERE request_public_id IS NOT NULL;
