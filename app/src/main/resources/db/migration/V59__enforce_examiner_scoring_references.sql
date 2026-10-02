-- Keep scoring-owned references consistent and require an existing submitted
-- Examiner score before an answer can select the Examiner as its source.
-- Rollback before score-source selection is in use: remove the two selected-score
-- constraints, clear the nullable pointer, drop that column, then remove the
-- composite foreign keys and unique constraints below. Once Phase 04 is live,
-- retain the pointer data and roll forward instead of dropping this column.

ALTER TABLE examiner_assignment_batches
    ADD CONSTRAINT uq_examiner_batch_public_scope
        UNIQUE (public_id, tenant_id, session_public_id);

ALTER TABLE examiner_attempt_assignments
    ADD CONSTRAINT uq_examiner_assignment_owner_scope
        UNIQUE (tenant_id, session_public_id, attempt_public_id, examiner_public_id),
    ADD CONSTRAINT fk_examiner_assignment_batch_scope
        FOREIGN KEY (batch_public_id, tenant_id, session_public_id)
        REFERENCES examiner_assignment_batches (public_id, tenant_id, session_public_id);

ALTER TABLE scoring_answers
    ADD COLUMN selected_examiner_score_public_id UUID,
    ADD CONSTRAINT uq_scoring_answer_owner_scope
        UNIQUE (answer_public_id, tenant_id, session_public_id, attempt_public_id);

ALTER TABLE examiner_answer_scores
    ADD CONSTRAINT uq_examiner_score_source_reference
        UNIQUE (public_id, answer_public_id, tenant_id, session_public_id, attempt_public_id),
    ADD CONSTRAINT fk_examiner_score_answer_scope
        FOREIGN KEY (answer_public_id, tenant_id, session_public_id, attempt_public_id)
        REFERENCES scoring_answers (answer_public_id, tenant_id, session_public_id, attempt_public_id),
    ADD CONSTRAINT fk_examiner_score_assignment_owner
        FOREIGN KEY (tenant_id, session_public_id, attempt_public_id, examiner_public_id)
        REFERENCES examiner_attempt_assignments (tenant_id, session_public_id, attempt_public_id, examiner_public_id);

ALTER TABLE scoring_answers
    ADD CONSTRAINT ck_scoring_answers_selected_examiner_reference
        CHECK ((selected_score_source = 'EXAMINER' AND selected_examiner_score_public_id IS NOT NULL)
            OR (selected_score_source IS DISTINCT FROM 'EXAMINER' AND selected_examiner_score_public_id IS NULL)),
    ADD CONSTRAINT fk_scoring_answer_selected_examiner_score
        FOREIGN KEY (selected_examiner_score_public_id, answer_public_id, tenant_id, session_public_id, attempt_public_id)
        REFERENCES examiner_answer_scores (public_id, answer_public_id, tenant_id, session_public_id, attempt_public_id);
