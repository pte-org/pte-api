-- A host can ask for an exam that prefers questions the centre has not used in its earlier exams.
ALTER TABLE exam_sessions ADD COLUMN avoid_repeated_questions boolean NOT NULL DEFAULT false;
