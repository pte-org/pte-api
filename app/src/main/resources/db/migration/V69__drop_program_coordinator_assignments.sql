-- The Program Coordinator business feature (assigning a Host user as a Program's
-- coordinator) has been removed entirely — no controller, service, or repository
-- reference this table anymore. Drop it rather than leave an orphaned table behind.
DROP TABLE IF EXISTS program_coordinator_assignments;
