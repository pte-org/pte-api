-- itembank module (plans/score-template-exam-generation, Plan B / Phase 1).
-- Supports the two exam-generation queries added to QuestionRepository:
-- the grouped PUBLISHED+SHARED count by task type, and the per-task-type
-- ORDER BY random() draw. Filters on (pte_task_type, status, visibility) —
-- not tenant_id, because generation pool is a platform-wide literal
-- visibility = 'SHARED', not tenant-scoped.

CREATE INDEX idx_questions_generation_pool
    ON questions (pte_task_type, status, visibility);
