-- Database-per-service (CODING_STANDARDS_MICROSERVICE.md §3, ADR-003).
-- Each service owns its own database + role. Co-located on one Postgres instance
-- for now; splitting to separate instances later is a connection-string change,
-- not a code change. Passwords here are LOCAL DEV ONLY — real envs inject via
-- Vault/secret manager (never commit real secrets).

-- Helper: create a role + database owned by it, idempotently.
DO $$
DECLARE
    svc TEXT;
    services TEXT[] := ARRAY[
        'iam', 'admin', 'authoring', 'scheduling', 'exam_delivery',
        'proctor', 'scoring', 'reporting', 'notification', 'media'
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
-- Guarded by \gexec pattern via psql; if a DB exists the CREATE is skipped by the shell wrapper.
SELECT 'CREATE DATABASE iam OWNER iam_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'iam')\gexec
SELECT 'CREATE DATABASE admin OWNER admin_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'admin')\gexec
SELECT 'CREATE DATABASE authoring OWNER authoring_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'authoring')\gexec
SELECT 'CREATE DATABASE scheduling OWNER scheduling_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'scheduling')\gexec
SELECT 'CREATE DATABASE exam_delivery OWNER exam_delivery_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'exam_delivery')\gexec
SELECT 'CREATE DATABASE proctor OWNER proctor_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'proctor')\gexec
SELECT 'CREATE DATABASE scoring OWNER scoring_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'scoring')\gexec
SELECT 'CREATE DATABASE reporting OWNER reporting_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'reporting')\gexec
SELECT 'CREATE DATABASE notification OWNER notification_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'notification')\gexec
SELECT 'CREATE DATABASE media OWNER media_svc'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'media')\gexec
