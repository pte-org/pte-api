-- Describe Image jobs choose how the picture is made: a chart from data, an AI-painted picture, or AUTO by topic.
ALTER TABLE question_generation_jobs
    ADD COLUMN image_kind VARCHAR(16) NOT NULL DEFAULT 'AUTO'
        CHECK (image_kind IN ('AUTO', 'CHART', 'PICTURE'));

-- A topic may now be a short brief ("economy, IT workers and jobs today, as a chart"), not only a word or two.
ALTER TABLE question_generation_jobs ALTER COLUMN topic_hint TYPE VARCHAR(500);
