-- scoring module (plans/score-template-exam-generation, Plan A / Phase 4).
-- Copies the submitting attempt's pinned ScoreTemplate onto ScoringAnswer at
-- ingest time (spec FR-07) so scoringMethod resolves from the template
-- instead of the deleted hardcoded AI/objective task-type catalogs. NOT NULL with
-- no default: safe only because the system is not yet deployed — a local
-- dev DB with pre-existing scoring_answers rows must be reset before this
-- migration runs (same caveat as V15/V16).

ALTER TABLE scoring_answers
    ADD COLUMN score_template_public_id UUID NOT NULL;
