-- Per-item scoring lifecycle for practice answers scored through the exam scoring module.
-- Additive only: existing rubric/explanation columns stay until the removal migration.
alter table practice_session_items
    add column if not exists scoring_status varchar(16) not null default 'NONE',
    add column if not exists scoring_dispatch_token uuid,
    add column if not exists scoring_requested_at timestamptz,
    add column if not exists scoring_dispatched_at timestamptz,
    add column if not exists scoring_attempts integer not null default 0,
    add column if not exists scoring_redispatch_count integer not null default 0,
    add column if not exists scoring_failure_code varchar(32),
    add column if not exists ai_feedback text;

-- Answers that already carry a result are final under the new lifecycle.
update practice_session_items
   set scoring_status = 'SCORED'
 where scoring_status = 'NONE'
   and (ai_score is not null or objective_score is not null);

-- Recovery and the per-user rate limit only look at rows that are (or were recently) in flight.
create index if not exists idx_practice_session_items_scoring_requested
    on practice_session_items (scoring_requested_at)
    where scoring_requested_at is not null;
