-- CEFR mix a centre chose for one exam, overriding the mix of the template the exam was built from.
-- No rows means the exam follows its template.
CREATE TABLE exam_session_cefr_targets (
    session_id bigint NOT NULL REFERENCES exam_sessions (id),
    level character varying(2) NOT NULL,
    percent integer NOT NULL,
    PRIMARY KEY (session_id, level),
    CONSTRAINT exam_session_cefr_targets_level_check CHECK (level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    CONSTRAINT exam_session_cefr_targets_percent_check CHECK (percent > 0 AND percent <= 100)
);
