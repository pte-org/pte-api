-- The study chatbot's prompts are edited like the other AI prompts, so the kind list gains CHATBOT.
ALTER TABLE ai_prompt_versions DROP CONSTRAINT IF EXISTS ai_prompt_versions_kind_check;
ALTER TABLE ai_prompt_versions ADD CONSTRAINT ai_prompt_versions_kind_check
    CHECK (kind IN ('GENERATOR', 'REVIEWER', 'SCORER', 'SHARED', 'CHATBOT'));
