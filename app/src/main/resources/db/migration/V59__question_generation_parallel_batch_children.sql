-- A batch now runs several children at once (as many as the parallel-run setting), and they all belong to the same
-- requester. The one-active-job rule stays for standalone jobs only; a batch is limited by its own
-- one-open-batch-per-requester index (V53).
DROP INDEX uk_qgen_jobs_one_active_per_requester;
CREATE UNIQUE INDEX uk_qgen_jobs_one_active_per_requester ON question_generation_jobs (requested_by_user_public_id)
    WHERE status IN ('QUEUED', 'RUNNING', 'CANCELLING') AND save_questions AND batch_id IS NULL;
