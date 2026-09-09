-- CELL ASYNC — scoring.
--
-- Isolated because its latency is NOT under our control: an AI vendor call holds
-- a thread for 30s+, and scoring runs right after one exam session while another
-- may still be in progress. Queue-fed and host-gated by design (a student never
-- waits on it), so downtime here is absorbed by RabbitMQ rather than felt.
--
-- Passwords here are LOCAL DEV ONLY — real envs inject via Vault/secret manager.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'scoring_svc') THEN
        CREATE ROLE scoring_svc LOGIN PASSWORD 'scoring_dev_pw';
    END IF;
END
$$;

SELECT 'CREATE DATABASE scoring OWNER scoring_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'scoring')\gexec
