-- JSON report stored with each generated exam: the algorithm version, seed, final question counts per task
-- type, planned duration per part and why the planner was or was not used. Null for exams built before it.
ALTER TABLE exam_versions ADD COLUMN assembly_report text;
