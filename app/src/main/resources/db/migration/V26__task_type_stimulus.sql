-- Some task types play an audio stimulus before the answer timer starts (Repeat Sentence, Re-tell Lecture,
-- Answer Short Question, Summarize Group Discussion, Respond to a Situation). Their playing time is not part of
-- the preparation or answer time, so the template's planned duration adds it. For every other task type the
-- listed answer time already covers the stimulus (Listening) or there is none (Reading, Writing).
--
-- stimulus_estimate_seconds is the fallback used while no approved question of the type has a measured audio
-- length; once some do, the median of their lengths is used instead.
ALTER TABLE question_types
    ADD COLUMN stimulus_before_timer boolean NOT NULL DEFAULT false,
    ADD COLUMN stimulus_estimate_seconds integer NOT NULL DEFAULT 0,
    ADD CONSTRAINT question_types_stimulus_estimate_check CHECK (stimulus_estimate_seconds >= 0);

UPDATE question_types AS t
SET stimulus_before_timer = true, stimulus_estimate_seconds = s.seconds
FROM (VALUES ('REPEAT_SENTENCE', 6), ('RE_TELL_LECTURE', 85), ('ANSWER_SHORT_QUESTION', 5),
             ('SUMMARIZE_GROUP_DISCUSSION', 86), ('RESPOND_TO_A_SITUATION', 18)) AS s(task_type_key, seconds)
WHERE t.task_type_key = s.task_type_key;
