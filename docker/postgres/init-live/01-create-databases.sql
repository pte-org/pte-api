-- CELL LIVE — proctor WebSocket sessions.
--
-- Separated from CELL EXAM despite serving the same exam window, because the two
-- exhaust DIFFERENT resources and peak simultaneously: proctor is bound by the
-- number of concurrent long-lived WS connections (file descriptors, per-connection
-- memory), exam-delivery is bound by request latency. Co-locating them means the
-- pair contend for the same box at exactly the moment load is highest.
--
-- Failure here degrades gracefully: proctors lose live monitoring, students keep
-- taking the exam.
--
-- Passwords here are LOCAL DEV ONLY — real envs inject via Vault/secret manager.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'proctor_svc') THEN
        CREATE ROLE proctor_svc LOGIN PASSWORD 'proctor_dev_pw';
    END IF;
END
$$;

SELECT 'CREATE DATABASE proctor OWNER proctor_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'proctor')\gexec
