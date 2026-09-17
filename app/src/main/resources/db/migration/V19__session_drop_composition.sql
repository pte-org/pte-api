-- session module (plans/score-template-exam-generation, Plan B / Phase 3).
-- SessionComposition is removed entirely: a student now pins every item of
-- the session's snapshot (the skill selection already happened at
-- exam-generation time, in assessment). System not yet deployed — safe to
-- drop without a backfill/migration path, same caveat as V15-V18.

DROP TABLE IF EXISTS session_compositions;
