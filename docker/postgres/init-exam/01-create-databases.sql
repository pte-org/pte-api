-- CELL EXAM — the critical path. The ONLY thing in this platform that cannot be
-- retried: a student mid-exam losing their attempt is unrecoverable, whereas
-- delayed scoring or a failed report can simply be re-run.
--
-- Isolated onto its own Postgres instance so that nothing else in the platform
-- (an admin bulk-import, a reporting rebuild, a scoring backlog) can exhaust the
-- connections/disk/locks this service needs while an exam is in progress.
--
-- Passwords here are LOCAL DEV ONLY — real envs inject via Vault/secret manager.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'exam_delivery_svc') THEN
        CREATE ROLE exam_delivery_svc LOGIN PASSWORD 'exam_delivery_dev_pw';
    END IF;
END
$$;

SELECT 'CREATE DATABASE exam_delivery OWNER exam_delivery_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'exam_delivery')\gexec
