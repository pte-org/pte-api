CREATE TABLE IF NOT EXISTS retry_request (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    student_id VARCHAR(255) NOT NULL,
    attempt_id BIGINT NOT NULL REFERENCES exam_attempt(id),
    exam_id BIGINT NOT NULL REFERENCES exam(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CONSUMED')),
    requested_at TIMESTAMPTZ NOT NULL,
    reviewed_at TIMESTAMPTZ,
    reviewed_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_retry_request_student_exam ON retry_request(student_id, exam_id);
CREATE INDEX IF NOT EXISTS idx_retry_request_status ON retry_request(status);
