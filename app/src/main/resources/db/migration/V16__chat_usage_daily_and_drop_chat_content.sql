-- Student chat no longer keeps what students ask or what the assistant answers: the conversation lives in the
-- student's browser tab only. What stays is the daily quota counter (chat_usage, one row per student and day) and,
-- new here, a daily table of counts and tokens for the admin cost screen. It has no student id and no text.

-- One row per Vietnam day and provider model. The report adds the cost up from here; cost_usd is the estimate
-- made when each message was counted, so a later price or model change never rewrites an earlier day.
CREATE TABLE chat_usage_daily (
    usage_date DATE NOT NULL,
    model VARCHAR(64) NOT NULL,
    messages INTEGER NOT NULL DEFAULT 0 CHECK (messages >= 0),
    ok INTEGER NOT NULL DEFAULT 0 CHECK (ok >= 0),
    refused INTEGER NOT NULL DEFAULT 0 CHECK (refused >= 0),
    failed INTEGER NOT NULL DEFAULT 0 CHECK (failed >= 0),
    tokens_in BIGINT NOT NULL DEFAULT 0 CHECK (tokens_in >= 0),
    tokens_out BIGINT NOT NULL DEFAULT 0 CHECK (tokens_out >= 0),
    tokens_thinking BIGINT NOT NULL DEFAULT 0 CHECK (tokens_thinking >= 0),
    cost_usd NUMERIC(14, 6) NOT NULL DEFAULT 0 CHECK (cost_usd >= 0),
    PRIMARY KEY (usage_date, model)
);

-- Carry the existing totals over before the messages go. The day is the Vietnam day, as in the old report.
-- The cost uses the default prices the report applied until now (0.10 in, 0.40 out per million tokens, thinking
-- counted as output); it is an estimate, like every figure on that screen.
INSERT INTO chat_usage_daily (usage_date, model, messages, ok, refused, failed, tokens_in, tokens_out,
                              tokens_thinking, cost_usd)
SELECT (created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
       coalesce(model, 'unknown'),
       count(*),
       count(*) FILTER (WHERE status = 'OK'),
       count(*) FILTER (WHERE status = 'REFUSED'),
       count(*) FILTER (WHERE status = 'FAILED'),
       coalesce(sum(tokens_in), 0),
       coalesce(sum(tokens_out), 0),
       coalesce(sum(tokens_thinking), 0),
       round((coalesce(sum(tokens_in), 0) * 0.10
              + (coalesce(sum(tokens_out), 0) + coalesce(sum(tokens_thinking), 0)) * 0.40) / 1000000, 6)
FROM chat_message
WHERE deleted = false
GROUP BY 1, 2;

-- What students typed and were answered, the consent records and the shared answer cache.
DROP TABLE chat_message;
DROP TABLE chat_session;
DROP TABLE chat_consent;
DROP TABLE chat_answer_cache;
