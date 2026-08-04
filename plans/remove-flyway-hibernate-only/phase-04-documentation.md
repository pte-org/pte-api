# Phase 4: Documentation

Covers user story: P2 (re-adding Flyway/JPA Buddy only happens on explicit
project-owner request, never automatically). Implements FR-05 and the third
spec Success Criteria bullet.

## Requirements
Project documentation clearly states the current dev-phase schema strategy,
states that Flyway + JPA Buddy are only reintroduced when the project owner
explicitly asks for it (no automatic trigger of any kind), and documents the
one-time manual local-DB-reset step each developer must perform.

## Steps
1. Add a schema-management section to the `pte-api` README (create the file
   if it doesn't exist) describing the current approach: Hibernate
   `ddl-auto=update`, no Flyway, one Postgres instance per developer.
2. Document the explicit re-add trigger word-for-word matching the spec:
   Flyway + JPA Buddy are reintroduced only when the project owner explicitly
   requests it — no date-based, completion-percentage, or other automatic
   condition triggers it.
3. Document the one-time manual step each developer must perform after
   pulling this change: drop their local `flyway_schema_history` table or
   recreate their local database.
4. State explicitly that `ddl-auto=update` must never be applied to a
   production or staging environment once those exist, per the NFR.
5. Document `ddl-auto=update`'s known operational limitations so devs aren't
   surprised day-to-day: (a) it cannot add a `NOT NULL` column to a table
   that already has rows — startup fails unless a default is supplied or the
   column is added nullable first and backfilled manually; (b) it never
   drops or renames columns/tables, so removing or renaming an entity field
   leaves a permanent orphan column in the local DB that must be cleaned up
   by hand.
6. Note that all `CREATE INDEX` statements from the deleted Flyway migrations
   were already mirrored as `@Index` annotations on the corresponding
   entities in Phase 2 (per Phase 1's inventory) — no indexes were dropped,
   so no further action is needed here beyond stating this for traceability.
7. Re-read the written note against the spec's FR-05 and NFR wording to
   confirm no extra or narrower condition was accidentally introduced.

## Success Criteria
- The `pte-api` README contains a section stating that Flyway/JPA Buddy
  re-add happens only on explicit project-owner request, with no other
  trigger condition.
- The same section includes the one-time manual local-DB-reset instruction
  for developers.
- The same section states `ddl-auto=update` is dev-only and must not reach
  production/staging.
- The same section lists `ddl-auto=update`'s NOT NULL and drop/rename
  limitations, and confirms the indexes from Phase 1's inventory were already
  re-added as `@Index` annotations in Phase 2 (nothing pending for later).

## Risks
- Documentation drifts out of sync with actual practice over time: mitigate
  by keeping the note short and placed prominently in the root README rather
  than buried in a per-service doc.
