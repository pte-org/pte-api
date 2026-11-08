-- Why a rubric version was activated, written by the approver. Additive; empty for earlier activations and for ones
-- whose sample scores moved little. The web form asks for it when the sample scores shift a lot.
ALTER TABLE scoring_rubric_activation_audits ADD COLUMN reason VARCHAR(1000);

-- Who last changed a rubric version (made it, edited it, sent it for review, approved or rejected it), for the list.
ALTER TABLE scoring_rubric_versions ADD COLUMN updated_by_user_public_id UUID;
