-- assessment module (plans/score-template-exam-generation, Plan A / Phase 2).
-- Pins the ACTIVE ScoreTemplate onto every ExamSnapshot at publish time
-- (spec FR-13, pin part). NOT NULL with no default: safe only because the
-- system is not yet deployed — a local dev DB with pre-existing
-- exam_snapshots rows must be reset before this migration runs (see
-- phase-02 Risks).

ALTER TABLE exam_snapshots
    ADD COLUMN score_template_public_id UUID NOT NULL,
    ADD COLUMN score_template_version INTEGER NOT NULL;
