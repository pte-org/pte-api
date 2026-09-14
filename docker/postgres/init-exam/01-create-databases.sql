-- CELL EXAM — the critical path. The ONLY thing in this platform that cannot be
-- retried: a student mid-exam losing their attempt is unrecoverable, whereas
-- delayed scoring or a failed report can simply be re-run.
--
-- Isolated onto its own Postgres instance so that nothing else in the platform
-- (an admin bulk-import, a reporting rebuild, a scoring backlog) can exhaust the
-- connections/disk/locks this service needs while an exam is in progress.
--
-- Credentials are supplied by the container environment. There are deliberately
-- no fallback values in this public init script.
\getenv db_user EXAM_DELIVERY_DB_USER
\getenv db_password EXAM_DELIVERY_DB_PASSWORD

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_user', :'db_password')
    WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_user')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', 'exam_delivery', :'db_user')
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'exam_delivery')\gexec
