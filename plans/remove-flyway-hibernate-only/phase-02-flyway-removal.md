# Phase 2: Flyway Removal (all 9 services)

Covers user stories: P1 ("no more `db/migration`, no more Flyway deps, all 9
services build"), P1 (entity changes auto-reflect in schema — enabled by the
`ddl-auto=update` switch made here). Implements FR-01, FR-02, FR-03.

## Requirements
Each of the 9 services (admin, authoring, exam-delivery, iam, media,
notification, proctor, reporting, scheduling, scoring) no longer declares a
Flyway dependency, no longer has a `spring.flyway` config block, uses
`ddl-auto=update` instead of `validate`, and no longer ships a
`db/migration` directory.

## Steps
1. Remove the `org.flywaydb:flyway-core` and
   `org.flywaydb:flyway-database-postgresql` dependency declarations from
   each service's `pom.xml`.
2. Remove the `spring.flyway` config block (the `enabled`/`locations` keys)
   from each service's `application.yml`.
3. Change `spring.jpa.hibernate.ddl-auto` from `validate` to `update` in each
   service's `application.yml`.
4. Delete the `src/main/resources/db/migration` directory, including all its
   SQL files, from each service.
5. Apply any entity/schema fix identified during the Phase 1 audit, for any
   service flagged there.
6. For every `CREATE INDEX` statement inventoried in Phase 1's
   `## Audit Results`, add an equivalent `@Index` entry to the owning
   entity's `@Table(indexes = { ... })` annotation (matching index name,
   table, and column list) so `ddl-auto=update` recreates it — this replaces
   the SQL-level index definitions being deleted, so no index is silently
   lost.
7. Repeat steps 1–4 identically across all 9 services — this is the same
   mechanical edit repeated per service, not a design decision per service.
8. Run a final repo-wide case-insensitive search for "flyway" across every
   service's `pom.xml` and `application.yml`, and confirm no
   `db/migration` directory remains anywhere under `services/*/src`, to
   catch any service missed in the batch.

## Success Criteria
- A case-insensitive search for "flyway" across all `services/*/pom.xml` and
  `services/*/src/main/resources/application.yml` returns zero matches.
- No `services/*/src/main/resources/db/migration` directory exists anywhere
  in the repo.
- Every one of the 9 services' `application.yml` shows
  `hibernate.ddl-auto: update`.
- Every `CREATE INDEX` statement from Phase 1's inventory has a matching
  `@Index` annotation on the owning entity — zero indexes dropped silently.

## Risks
- Missing a service during the batched edit: mitigate with the mandatory
  repo-wide grep sweep in step 7 before moving to Phase 3.
- An entity fix identified in Phase 1 gets forgotten during the mechanical
  batch pass: mitigate by applying and re-confirming Phase 1 fixes as their
  own explicit sub-step (step 5) per affected service, not folded silently
  into the generic edit.
