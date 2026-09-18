-- attempt module (plans/score-template-exam-generation, Plan A / Phase 3).
-- Copies the source snapshot's pinned ScoreTemplate onto PinnedExamSnapshot
-- at attempt-pin time (spec FR-14). NOT NULL with no default: safe only
-- because the system is not yet deployed — a local dev DB with pre-existing
-- pinned_exam_snapshots rows must be reset before this migration runs (see
-- phase-03 Risks, same caveat as V15).

ALTER TABLE pinned_exam_snapshots
    ADD COLUMN score_template_public_id UUID NOT NULL;
