ALTER TABLE examiner_assignment_batches
    DROP CONSTRAINT examiner_assignment_batches_status_check;

ALTER TABLE examiner_assignment_batches
    ADD CONSTRAINT ck_examiner_assignment_batch_status
        CHECK (status IN ('PREVIEWED', 'COMMITTED', 'EXPIRED', 'STALE'));
