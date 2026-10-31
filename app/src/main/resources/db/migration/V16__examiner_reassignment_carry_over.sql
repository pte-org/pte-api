-- A draft handed over when the Host reassigns an attempt keeps the previous examiner here until the new examiner
-- saves it again; finalizing is blocked while any draft of the work still carries it.
ALTER TABLE answer_evaluations
    ADD COLUMN carried_over_from_examiner_public_id uuid;
