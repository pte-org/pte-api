# pte-api

## Running services locally with Docker Compose

Two deployment options for local development:

**Option 1: Infrastructure only**

Run just the database, cache, and message broker (useful when running services from your IDE):

```bash
docker compose -f docker-compose.yml up
```

**Option 2: Full stack with services**

Run the API gateway and all 10 backend services alongside infrastructure:

```bash
docker compose -f docker-compose.yml -f docker-compose.services.yml up --build
```

Services are built automatically from the shared `Dockerfile`. The API gateway is accessible on `http://localhost:8080`:

```bash
curl http://localhost:8080/actuator/health
```

Backend services (admin, authoring, exam-delivery, iam, media, notification, proctor, reporting, scheduling, scoring) communicate via the internal Docker network and are not exposed on host ports.

### Populating test data for end-to-end testing

After bringing up the full stack (Option 2), seed test data using `scripts/seed-e2e.ps1`:

**Prerequisites (one-time setup):**

Before running the seed script, bootstrap a `PLATFORM_ADMIN` account directly via SQL — pte-api has no self-registration endpoint. This requires:

1. Generate a BCrypt hash for your bootstrap password using Docker (easiest — `htpasswd` is not bundled with all Git Bash installs):

```powershell
docker run --rm httpd:2.4-alpine htpasswd -nbBC 10 "" 'Password123!'
# Output: :$2a$10$... (copy the hash part after the colon)
```

2. Connect to the `iam` database and run this SQL once:

```sql
INSERT INTO users (public_id, email, full_name, tenant_id, status, deleted, created_at, updated_at)
VALUES (gen_random_uuid(), 'admin@test.local', 'Bootstrap Admin', NULL, 'ACTIVE', false, now(), now())
RETURNING id;
-- Capture the printed id value, use it as <USER_ID> in the next two statements

INSERT INTO user_roles (user_id, role) VALUES (<USER_ID>, 'PLATFORM_ADMIN');

INSERT INTO login_hashes (public_id, user_id, hash, deleted, created_at, updated_at)
VALUES (gen_random_uuid(), <USER_ID>, '<BCRYPT_HASH>', false, now(), now());
```

**Running the seed script:**

```powershell
# From the pte-api directory, with the stack running:
.\scripts\seed-e2e.ps1
# or with custom credentials:
.\scripts\seed-e2e.ps1 -BootstrapAdminEmail admin@example.com -BootstrapAdminPassword YourPassword123!
```

The script is idempotent — rerun it any time to recreate the test tenant, host, student, and seeded exam sessions. It talks only to the public gateway API (the same path a real client uses), so it is safe to run repeatedly.

It seeds two published questions, each in its own session: a `READ_ALOUD` question (text-only) and a `REPEAT_SENTENCE` question (with a real uploaded audio file as its prompt — see `scripts/fixtures/repeat_sentence_sample.wav`; pass `-RepeatSentenceAudioFixturePath` to use a different file). `ExamAttempt`s are one-shot per (student, session) — once a session's attempt is submitted/completed, that student can't restart it; re-run the script with a different `-SessionName`/`-RepeatSentenceSessionName` to seed a fresh session instead.

## Timezone (UTC, fleet-wide)

As of 2026-08-05, every service forces its JVM default timezone to UTC as
the first line of `main()` (`TimeZone.setDefault(TimeZone.getTimeZone
("UTC"))`), independent of the host OS's configured timezone — this is
what makes `postgres:17`'s `TimeZone` connection parameter always resolve
correctly, regardless of which timezone your dev machine is set to (e.g.
avoids the `Asia/Saigon` IANA alias that `postgres:17`'s tzdata doesn't
recognize; only the canonical `Asia/Ho_Chi_Minh` name is present there).

This is already handled in code — you don't need to do anything to get it.
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
`spring.jpa.hibernate.ddl-auto=update` — there are no SQL migration files
to write or maintain during this dev phase. See
`plans/remove-flyway-hibernate-only/spec.md` for the full rationale.

### Re-adding Flyway + JPA Buddy

Flyway and JPA Buddy are reintroduced **only when the project owner
explicitly requests it** — there is no automatic trigger (not by date, not
by "feature complete", not by any other condition). Do not add Flyway back
on your own judgment; wait for an explicit request.

### One-time local setup after pulling this change

If your local Postgres database(s) were previously managed by Flyway,
drop the `flyway_schema_history` table (or drop/recreate the affected
database) once before starting a service. This is a manual, per-developer
step — it is not scripted or automated.

### One-time local DB reset after pulling the UTC/`timestamptz` change

As of 2026-08-05 (`plans/postgres-utc-timestamptz-migration`), every
timestamp column is expected to be `timestamp with time zone`. Hibernate's
`ddl-auto=update` does **not** retroactively change an existing column's
type — if your local database(s) were created before this change, their
timestamp columns are still `timestamp without time zone` even after
pulling. Drop and recreate the affected database(s) once (same manual,
per-developer step as the Flyway-removal reset above) so a fresh
`ddl-auto=update` run materializes `timestamptz` columns from the (already
`Instant`-typed) entity fields. No SQL migration is written for this —
consistent with this project's dev-only `ddl-auto=update` approach.

### `ddl-auto=update` is dev-only

`ddl-auto=update` must **never** be applied to a production or staging
environment. No such environment exists yet for this project; when one is
created, schema management must go through an explicit, project-owner-
requested reintroduction of Flyway first.

### Known `ddl-auto=update` limitations

- **Cannot add a `NOT NULL` column to a table that already has rows** —
  startup fails unless a default is supplied, or the column is added
  nullable first and backfilled manually.
- **Never drops or renames columns/tables** — removing or renaming an
  entity field leaves a permanent orphan column in the local database that
  must be cleaned up by hand.
- **Indexes**: all indexes that previously came from the deleted Flyway
  migrations were mirrored as `@Index` annotations on the corresponding
  entities when Flyway was removed — nothing is pending here.
