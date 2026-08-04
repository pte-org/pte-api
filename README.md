# pte-api

## Database schema management (dev phase)

As of 2026-08-04, all 9 services (admin, authoring, exam-delivery, iam,
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
