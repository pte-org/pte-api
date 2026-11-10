-- Pearson time window of a template, in seconds: the range each part of the test may take and a ceiling for
-- the whole test. Null columns mean the template carries no time limits (host templates read the frame of
-- the platform template they were cloned from).
ALTER TABLE exam_definitions
    ADD COLUMN sw_min_seconds integer,
    ADD COLUMN sw_max_seconds integer,
    ADD COLUMN reading_min_seconds integer,
    ADD COLUMN reading_max_seconds integer,
    ADD COLUMN listening_min_seconds integer,
    ADD COLUMN listening_max_seconds integer,
    ADD COLUMN total_max_seconds integer,
    ADD CONSTRAINT exam_definitions_time_frame_check CHECK (
        (sw_min_seconds IS NULL AND sw_max_seconds IS NULL AND reading_min_seconds IS NULL
            AND reading_max_seconds IS NULL AND listening_min_seconds IS NULL
            AND listening_max_seconds IS NULL AND total_max_seconds IS NULL)
        OR (sw_min_seconds > 0 AND sw_max_seconds >= sw_min_seconds
            AND reading_min_seconds > 0 AND reading_max_seconds >= reading_min_seconds
            AND listening_min_seconds > 0 AND listening_max_seconds >= listening_min_seconds
            AND total_max_seconds >= sw_min_seconds + reading_min_seconds + listening_min_seconds));

-- Score Guide, section 7: Speaking and Writing 76-84 minutes, Reading 23-30, Listening 31-39; the test lasts
-- at most 2 hours 15 minutes. Applied to the ACTIVE platform template and the platform drafts cloned from it.
UPDATE exam_definitions
SET sw_min_seconds = 4560, sw_max_seconds = 5040,
    reading_min_seconds = 1380, reading_max_seconds = 1800,
    listening_min_seconds = 1860, listening_max_seconds = 2340,
    total_max_seconds = 8100
WHERE tenant_id IS NULL AND status IN ('ACTIVE', 'DRAFT', 'PENDING_APPROVAL');
