ALTER TABLE score_source_audits
    ADD COLUMN request_public_id UUID,
    ADD COLUMN previous_ai_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN previous_examiner_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN previous_unselected_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_score_source_audit_previous_counts CHECK (
        previous_ai_count >= 0 AND previous_examiner_count >= 0 AND previous_unselected_count >= 0
    );

CREATE UNIQUE INDEX uq_score_source_audit_request
    ON score_source_audits (tenant_id, session_public_id, request_public_id);

ALTER TABLE attempt_reports
    ADD COLUMN report_snapshot_json TEXT,
    ADD COLUMN publication_public_id UUID,
    ADD COLUMN published_by_public_id UUID,
    ADD COLUMN publication_cohort_size INTEGER;

ALTER TABLE attempt_reports
    ADD CONSTRAINT ck_attempt_report_publication_cohort_size
        CHECK (publication_cohort_size IS NULL OR publication_cohort_size > 0);
