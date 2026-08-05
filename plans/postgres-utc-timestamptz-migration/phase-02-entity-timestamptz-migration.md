# Phase 2: Entity Timestamp Verification & Local DB Reset

Covers user stories: P1 ("all timestamp columns show `timestamp with time
zone` in `psql \d`, generated purely from `Instant` fields via
`ddl-auto=update`"). Implements FR-02, FR-04.

## Requirements
Every entity's timestamp field across all 10 services is confirmed to use
`Instant` (not `LocalDateTime`/`OffsetDateTime`), and each developer's local
Postgres database is reset so Hibernate's `ddl-auto=update` materializes
those fields as genuine `timestamp with time zone` columns — no hand-written
`ALTER COLUMN` SQL.

## Steps
1. Re-run a repo-wide grep for `LocalDateTime` and `OffsetDateTime` across
   every `.java` file under `pte-api`, scoped to `domain`/entity packages
   first. This session's planning-time grep found **zero** matches (every
   entity already uses `Instant`, including via `BaseEntity`'s
   `createdAt`/`updatedAt`) — treat that as the starting hypothesis to
   confirm, not a given, since a field may have been added since planning.
2. For any entity field found still using `LocalDateTime` or
   `OffsetDateTime` (contingency — none expected per step 1's planning-time
   result), convert its declared type to `Instant` with a plain `@Column`
   (no `@JdbcTypeCode` or `hibernate.jdbc.time_zone` override needed for
   `Instant` under Hibernate 6.x) and update every read/write call site
   accordingly.
3. For each of the 10 services, drop and recreate its local Postgres
   database (full reset, not `ALTER COLUMN ... TYPE timestamptz`) —
   consistent with the `remove-flyway-hibernate-only` precedent for this
   dev-only, one-instance-per-developer environment.
4. Start each of the 10 services once each so `ddl-auto=update` auto-creates
   the fresh schema, including every timestamp column as `timestamptz`.
5. Run `psql \d <table>` (or `\d+`) against every table with a timestamp
   column, across all 10 service databases, and confirm each shows
   `timestamp with time zone`, not `timestamp without time zone`.
6. Spot-check correctness (not just type) for at least one entity per
   service with a timestamp field: write a row, read it back, and confirm
   the value round-trips without an off-by-timezone shift.
7. Document the one-time manual local DB reset step for other developers
   (mirroring the existing `pte-api/README.md` section from the Flyway
   removal), so nobody hits a confusing stale-schema error later.

## Success Criteria
- A repo-wide grep for `LocalDateTime`/`OffsetDateTime` across all `.java`
  files under `pte-api` returns zero matches (spec's FR-02 success
  criterion, re-verified at execution time).
- `psql \d` on every table with a timestamp column, across all 10 service
  databases, shows `timestamp with time zone`.
- The round-trip spot-check in step 6 shows no off-by-timezone discrepancy
  for at least one entity per service.
- `pte-api/README.md` (or equivalent dev doc) documents the one-time local
  DB reset step for this change.

## Risks
- Step 1's grep finds a stray `LocalDateTime`/`OffsetDateTime` field this
  planning pass missed: mitigated by treating step 1 as mandatory and
  re-verified at execution time, not skipped based on this plan's finding.
- A developer's local DB reset is skipped, leaving stale
  `timestamp without time zone` columns from a schema created before this
  change: mitigated by step 7's explicit documentation, matching the
  existing Flyway-removal precedent developers are already familiar with.
- `ddl-auto=update` cannot retroactively change an existing column's type on
  a database that isn't fully reset: mitigated by step 3's full drop/recreate
  instead of relying on `ddl-auto=update` to migrate in place.
