-- Rename only the seeded standard template; preserve its identity and task configuration.
UPDATE exam_definitions
SET name = 'PTE 22 task type', updated_at = CURRENT_TIMESTAMP
WHERE public_id = 'a0b7f8b5-115d-417d-b7f8-0e03206850ec'
  AND code = 'PTE_Score_Template'
  AND version = 1
  AND name = 'APEUni PTE Score Table V5';
