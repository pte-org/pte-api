-- Phase 6: retain the immutable score-template version in the attempt-local
-- scoring context. Existing attempts remain readable; new pins populate it
-- from the published snapshot (or the legacy template lookup fallback).
ALTER TABLE pinned_exam_snapshots
    ADD COLUMN IF NOT EXISTS score_template_version INTEGER;
