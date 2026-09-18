-- Carried over from a deleted ExamTemplate-era migration (V22, dropped when
-- the ExamTemplate system was removed in the dev merge — see the merge
-- resolution plan). These two statements are unrelated to ExamTemplate and
-- are still required by the kept design:
--   1. AttemptReport.snapshotPublicId (com.pte.reporting.domain.AttemptReport)
--      has always needed this column; it was never in any other migration.
--   2. Every question is platform-owned (Visibility.PRIVATE no longer exists,
--      ItembankService.create() always sets tenant_id = null) — this CHECK
--      makes that invariant explicit at the database level.

ALTER TABLE attempt_reports ADD COLUMN IF NOT EXISTS snapshot_public_id UUID;

ALTER TABLE questions
    ADD CONSTRAINT questions_platform_owned CHECK (tenant_id IS NULL);
