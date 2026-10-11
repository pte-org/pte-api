-- Matches tenant schedule scanning/order and inverse Proctor assignment lookup.
-- Rollback, if required: drop these two additive indexes (no data/schema columns change).
CREATE INDEX idx_staff_session_schedule ON exam_sessions (tenant_id, opens_at DESC, public_id)
WHERE deleted = false;
CREATE INDEX idx_staff_proctor_schedule ON proctor_assignments (tenant_id, proctor_public_id, session_id)
WHERE deleted = false;
