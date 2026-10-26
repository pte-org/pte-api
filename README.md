# pte-api

## Secrets and environment files

`.env.example` is a public variable checklist and contains no usable credentials.
Copy it to the ignored `.env.local` for development, or inject the variables from
a secret manager in CI/CD. Compose and the Spring services intentionally fail fast
when required database, RabbitMQ, or service-to-service credentials are
missing; they no longer contain public fallback passwords or keys.

If a real `.env` value was ever committed, pasted into an issue/chat, or included
in a build log, rotate that credential in the corresponding service before using
the environment again. Editing the file alone does not revoke an exposed secret.
Changing an environment variable also does not rewrite credentials in an already
initialized named volume; rotate the existing Postgres/RabbitMQ accounts
explicitly, and do not use `docker compose down -v` unless deleting local data is
intentional.

## Running services locally with Docker Compose

Two deployment options for local development:

**Option 1: Infrastructure only**

Run just the database, cache, and message broker (useful when running services from your IDE):

```bash
docker compose --env-file .env.local -f docker-compose.yml up
```

**Option 2: Full stack with services**

Run `app` alongside infrastructure (gateway was removed â€” app is reached directly, no separate edge process):

```bash
docker compose --env-file .env.local -f docker-compose.yml -f docker-compose.services.yml up --build
```

`app` is built automatically from the shared `Dockerfile` and accessible directly on `http://localhost:8091` (the local Nginx API edge is `http://localhost:8080`):

```bash
curl http://localhost:8091/actuator/health
```

### Populating test data for end-to-end testing

Flyway applies one migration, `V1__baseline.sql` (the complete schema plus reference data and a demo seed), so an empty
database comes up ready to log in:

- `admin@test.local` (PLATFORM_ADMIN) and `host.e2e@test.local` (HOST_ADMIN of the `e2e` organization), BCrypt hashes
  only. The password is shared out of band; never write it into the repository.
- Task types, the active exam definition, a service plan, an active subscription, a program, classes and four students.

The 128 earlier migrations were retired (they remain in git history). A database that was migrated with the old history
cannot take `V1`; drop and recreate it (production included; see the deployment note in `AGENTS.md`).

For a full session -> attempt -> grading -> publication flow, run the scripts from the `pte-api` directory with the stack
running (`PTE_BASE_URL` defaults to `http://localhost:8080`; use `http://localhost:8091` against the bare `app`):

```powershell
$env:PTE_ADMIN_PASSWORD = '<local-admin-password>'
$env:PTE_SEED_PASSWORD  = '<local-seed-password>'
.\scripts\seed-local-template-and-question-bank.ps1 -SeedKey PTE_LOCAL
.\scripts\seed-local-exam-ready.ps1 -SeedKey PTE_LOCAL   # creates the tenant, host, student and an open exam
.\scripts\seed-local-platform-roles.ps1                  # optional: platform manager/support accounts
```

The scripts are idempotent. `seed-local-exam-ready.ps1` may fail once at its attempt preflight right after the exam
opens; run it again. For schema experiments, `APP_SPRING_PROFILES=local-schema` lets Hibernate build the schema instead
(see `pte-doc/projects/plans/quang-entity-consolidation/local-dev-runbook.md`).

## Timezone (UTC, fleet-wide)

As of 2026-08-05, every service forces its JVM default timezone to UTC as
the first line of `main()` (`TimeZone.setDefault(TimeZone.getTimeZone
("UTC"))`), independent of the host OS's configured timezone â€” this is
what makes `postgres:17`'s `TimeZone` connection parameter always resolve
correctly, regardless of which timezone your dev machine is set to (e.g.
avoids the `Asia/Saigon` IANA alias that `postgres:17`'s tzdata doesn't
recognize; only the canonical `Asia/Ho_Chi_Minh` name is present there).

This is already handled in code â€” you don't need to do anything to get it.
As a redundant belt-and-suspenders safety net (not required, the code-level
fix above already covers correctness), you may optionally also pass
`-Duser.timezone=UTC` when launching a service jar directly:

```
java -Duser.timezone=UTC -jar services/<service>/target/<service>-0.0.1-SNAPSHOT.jar
```

See `plans/postgres-utc-timestamptz-migration/spec.md` for the full
rationale.

## Database schema management (dev phase)

As of 2026-08-04, all 10 services (admin, authoring, exam-delivery, iam,
media, notification, proctor, reporting, scheduling, scoring) run without
Flyway. Schema is managed entirely by Hibernate via
`spring.jpa.hibernate.ddl-auto=update` â€” there are no SQL migration files
to write or maintain during this dev phase. See
`plans/remove-flyway-hibernate-only/spec.md` for the full rationale.

### Admin event-consumer migration exception

The tenant student-roster work explicitly introduces a versioned Admin schema
migration for `processed_events`. Admin therefore includes Flyway and loads
`services/admin/src/main/resources/db/migration` at startup. The migration is
idempotent and uses `baseline-on-migrate` so an existing Admin database can
adopt the ledger without recreating its current tables. This does not make
Hibernate schema updates safe for production; production must still set
`spring.jpa.hibernate.ddl-auto=validate` after applying migrations.

### Re-adding Flyway + JPA Buddy

Flyway and JPA Buddy are reintroduced **only when the project owner
explicitly requests it** â€” there is no automatic trigger (not by date, not
by "feature complete", not by any other condition). Do not add Flyway back
on your own judgment; wait for an explicit request.

### One-time local setup after pulling this change

If your local Postgres database(s) were previously managed by Flyway,
drop the `flyway_schema_history` table (or drop/recreate the affected
database) once before starting a service. This is a manual, per-developer
step â€” it is not scripted or automated.

### One-time local DB reset after pulling the UTC/`timestamptz` change

As of 2026-08-05 (`plans/postgres-utc-timestamptz-migration`), every
timestamp column is expected to be `timestamp with time zone`. Hibernate's
`ddl-auto=update` does **not** retroactively change an existing column's
type â€” if your local database(s) were created before this change, their
timestamp columns are still `timestamp without time zone` even after
pulling. Drop and recreate the affected database(s) once (same manual,
per-developer step as the Flyway-removal reset above) so a fresh
`ddl-auto=update` run materializes `timestamptz` columns from the (already
`Instant`-typed) entity fields. No SQL migration is written for this â€”
consistent with this project's dev-only `ddl-auto=update` approach.

### `ddl-auto=update` is dev-only

`ddl-auto=update` must **never** be applied to a production or staging
environment. No such environment exists yet for this project; when one is
created, schema management must go through an explicit, project-owner-
requested reintroduction of Flyway first.

### Known `ddl-auto=update` limitations

- **Cannot add a `NOT NULL` column to a table that already has rows** â€”
  startup fails unless a default is supplied, or the column is added
  nullable first and backfilled manually.
- **Never drops or renames columns/tables** â€” removing or renaming an
  entity field leaves a permanent orphan column in the local database that
  must be cleaned up by hand.
- **Indexes**: all indexes that previously came from the deleted Flyway
  migrations were mirrored as `@Index` annotations on the corresponding
  entities when Flyway was removed â€” nothing is pending here.
