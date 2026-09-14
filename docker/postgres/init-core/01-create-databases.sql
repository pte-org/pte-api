-- CELL CORE — control plane + authoring. Low traffic, tolerates 5-10 min downtime,
-- nothing in an active exam depends on it in real time (JWT is verified locally
-- against cached JWKS, so `iam` being down does not affect a student mid-exam).
--
-- Split out of the former single `postgres` instance (docker/postgres/init/) per
-- the cell-based re-architecture: 10 logical databases previously shared ONE
-- Postgres process, so a restart/disk-full/lock on it took down all 10 services
-- at once. Database-per-service (ADR-003) was logical only; this makes it physical.
--
-- Credentials are supplied by the container environment. There are deliberately
-- no fallback values in this public init script.
\getenv iam_user IAM_DB_USER
\getenv iam_password IAM_DB_PASSWORD
\getenv admin_user ADMIN_DB_USER
\getenv admin_password ADMIN_DB_PASSWORD
\getenv authoring_user AUTHORING_DB_USER
\getenv authoring_password AUTHORING_DB_PASSWORD
\getenv scheduling_user SCHEDULING_DB_USER
\getenv scheduling_password SCHEDULING_DB_PASSWORD
\getenv reporting_user REPORTING_DB_USER
\getenv reporting_password REPORTING_DB_PASSWORD
\getenv notification_user NOTIFICATION_DB_USER
\getenv notification_password NOTIFICATION_DB_PASSWORD
\getenv media_user MEDIA_DB_USER
\getenv media_password MEDIA_DB_PASSWORD

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'iam_user', :'iam_password')
    WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'iam_user')\gexec
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'admin_user', :'admin_password')
    WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'admin_user')\gexec
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'authoring_user', :'authoring_password')
    WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'authoring_user')\gexec
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'scheduling_user', :'scheduling_password')
    WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'scheduling_user')\gexec
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'reporting_user', :'reporting_password')
    WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'reporting_user')\gexec
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'notification_user', :'notification_password')
    WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'notification_user')\gexec
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'media_user', :'media_password')
    WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'media_user')\gexec

-- CREATE DATABASE cannot run inside the DO block (no transaction); list explicitly.
SELECT format('CREATE DATABASE %I OWNER %I', 'iam', :'iam_user')
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'iam')\gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'admin', :'admin_user')
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'admin')\gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'authoring', :'authoring_user')
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'authoring')\gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'scheduling', :'scheduling_user')
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'scheduling')\gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'reporting', :'reporting_user')
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'reporting')\gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'notification', :'notification_user')
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'notification')\gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'media', :'media_user')
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'media')\gexec

-- Blast-radius guard for the admin <-> host contention this cell cannot solve by
-- splitting (admin and host operate on the SAME rows, so the database cannot be
-- split by actor without duplicating data). A runaway bulk-import from admin is
-- capped here instead of being allowed to starve host/proctor of connections.
ALTER ROLE :"admin_user" SET statement_timeout = '30s';
ALTER ROLE :"admin_user" CONNECTION LIMIT 20;
