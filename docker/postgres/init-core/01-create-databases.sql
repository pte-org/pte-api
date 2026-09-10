-- CELL CORE — control plane + authoring. Low traffic, tolerates 5-10 min downtime,
-- nothing in an active exam depends on it in real time (JWT is verified locally
-- against cached JWKS, so `iam` being down does not affect a student mid-exam).
--
-- Split out of the former single `postgres` instance (docker/postgres/init/) per
-- the cell-based re-architecture: 10 logical databases previously shared ONE
-- Postgres process, so a restart/disk-full/lock on it took down all 10 services
-- at once. Database-per-service (ADR-003) was logical only; this makes it physical.
--
-- Passwords here are LOCAL DEV ONLY — real envs inject via Vault/secret manager.

DO $$
DECLARE
    svc TEXT;
    services TEXT[] := ARRAY[
        'iam', 'admin', 'authoring', 'scheduling', 'reporting', 'notification', 'media'
    ];
BEGIN
    FOREACH svc IN ARRAY services LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = svc || '_svc') THEN
            EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', svc || '_svc', svc || '_dev_pw');
        END IF;
    END LOOP;
END
$$;

-- CREATE DATABASE cannot run inside the DO block (no transaction); list explicitly.
SELECT 'CREATE DATABASE iam OWNER iam_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'iam')\gexec
SELECT 'CREATE DATABASE admin OWNER admin_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'admin')\gexec
SELECT 'CREATE DATABASE authoring OWNER authoring_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'authoring')\gexec
SELECT 'CREATE DATABASE scheduling OWNER scheduling_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'scheduling')\gexec
SELECT 'CREATE DATABASE reporting OWNER reporting_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'reporting')\gexec
SELECT 'CREATE DATABASE notification OWNER notification_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'notification')\gexec
SELECT 'CREATE DATABASE media OWNER media_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'media')\gexec

-- Blast-radius guard for the admin <-> host contention this cell cannot solve by
-- splitting (admin and host operate on the SAME rows, so the database cannot be
-- split by actor without duplicating data). A runaway bulk-import from admin is
-- capped here instead of being allowed to starve host/proctor of connections.
ALTER ROLE admin_svc SET statement_timeout = '30s';
ALTER ROLE admin_svc CONNECTION LIMIT 20;
