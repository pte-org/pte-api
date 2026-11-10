-- Rubric version pin already exists on answer_evaluations from V32 and is shared by both evaluation sources.
ALTER TABLE answer_evaluations ADD COLUMN trait_scores_json TEXT;
COMMENT ON COLUMN answer_evaluations.trait_scores_json IS
    'Examiner Pearson traits: scale=PEARSON_V1, rubricVersion, traits; null for legacy total-only scores';
