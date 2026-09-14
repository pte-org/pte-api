-- CELL ASYNC — scoring.
--
-- Isolated because its latency is NOT under our control: an AI vendor call holds
-- a thread for 30s+, and scoring runs right after one exam session while another
-- may still be in progress. Queue-fed and host-gated by design (a student never
-- waits on it), so downtime here is absorbed by RabbitMQ rather than felt.
--
-- Credentials are supplied by the container environment. There are deliberately
-- no fallback values in this public init script.
\getenv db_user SCORING_DB_USER
\getenv db_password SCORING_DB_PASSWORD

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_user', :'db_password')
    WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_user')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', 'scoring', :'db_user')
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'scoring')\gexec
