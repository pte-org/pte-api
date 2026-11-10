-- Why an examiner's band differs from the AI's by two or more levels, per trait; read to calibrate the AI scorer.
ALTER TABLE answer_evaluations ADD COLUMN ai_gap_reasons_json TEXT;
COMMENT ON COLUMN answer_evaluations.ai_gap_reasons_json IS
    'Examiner reasons for differing from the AI band, trait key -> text; null when none were given';
