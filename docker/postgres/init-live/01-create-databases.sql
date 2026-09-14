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
-- Credentials are supplied by the container environment. There are deliberately
-- no fallback values in this public init script.
\getenv db_user PROCTOR_DB_USER
\getenv db_password PROCTOR_DB_PASSWORD

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_user', :'db_password')
    WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_user')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', 'proctor', :'db_user')
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'proctor')\gexec
