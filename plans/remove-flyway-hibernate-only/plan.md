# Plan: Remove Flyway, switch to Hibernate ddl-auto (dev phase)
Status: ✅ Complete — implementation, test, code-review PASSED
Date: 2026-08-04
Mode: Hard

## Overview
Removes Flyway (dependencies, config, and the 22 `db/migration` SQL files) from
all 9 pte-api services and switches each to Hibernate `ddl-auto=update` for
the current dev-only phase, per `plans/remove-flyway-hibernate-only/spec.md`.

## Phases
- [x] Phase 1: Enum & Schema Audit — grep all 9 services for native Postgres
      enum types and `@Enumerated` mappings before any deletion, to catch the
      known `column is of type X but expression is of type character
      varying` failure mode ahead of time.
- [x] Phase 2: Flyway Removal (all 9 services) — strip `flyway-core` /
      `flyway-database-postgresql` from each `pom.xml`, delete the
      `spring.flyway` block, set `ddl-auto=update`, delete `db/migration`,
      mirror the 32 inventoried indexes as `@Index` annotations.
- [x] Phase 3: Build & Runtime Verification — `mvn clean install` (PASS, all
      13 modules) + test suite (PASS) + live Docker verification (PASS: JPA
      schema auto-creation confirmed for `admin`, entity-change trial
      confirmed) — user opted to have cook run this via Docker instead of by
      hand. One unrelated pre-existing issue found (see phase-03.md).
- [x] Phase 4: Documentation — created `pte-api/README.md` with the
      explicit-request-only Flyway/JPA Buddy re-add trigger, the one-time
      manual local-DB-reset step, the prod/staging boundary, and
      `ddl-auto=update`'s known limitations.

## Research Summary
Two researcher passes this session confirmed the following, incorporated
directly into the phases above:

- **Safest `ddl-auto` value:** `update` (not `create-drop`, not `validate`) —
  additive-safe for local dev data, matches spec assumption of one Postgres
  instance per developer.
- **Per-service mechanical checklist** (applied identically across all 9
  services in Phase 2): remove the two Flyway Maven deps, delete the
  `spring.flyway` config block, flip `ddl-auto` from `validate` to `update`,
  delete `db/migration/`, confirm with `mvn clean install`.
- **New risk surfaced and pre-empted:** Postgres native enum columns
  (`CREATE TYPE ... AS ENUM`) are the most likely startup-crash source if
  Hibernate's default enum handling doesn't match. A repo-wide grep run
  during planning found **zero** `CREATE TYPE` / `AS ENUM` statements across
  all `db/migration/V1__*.sql` files, and all `@Enumerated` usages found
  (admin, authoring, exam-delivery, iam, media, notification, proctor,
  scheduling, scoring) already use `EnumType.STRING`, consistent with the
  existing `VARCHAR`-typed migration columns. This means no known mismatch
  exists today — but Phase 1 keeps the audit as an explicit, repeatable step
  (not just a one-off finding) so it's re-verified at execution time and
  catches anything missed by the planning-time grep.
- **Confirmed zero risk areas** (no plan changes needed): no test in any
  `pte-api/**/src/test/**` uses Testcontainers or Flyway; no
  `docker-compose.yml` / `docker-compose.services.yml` references Flyway or
  migrations.
- **Migration file count:** 22 source `db/migration/*.sql` files across the 9
  services (44 counting duplicated build artifacts under each service's
  `target/classes/db/migration`, matching the spec's FR-03 figure) — all
  deleted outright in Phase 2, no baseline conversion.
- **Re-add trigger:** Flyway + JPA Buddy are only reintroduced later on
  explicit project-owner request — no automatic condition (date, completion
  %, etc.). This plan intentionally has **no** "re-add Flyway" phase; that
  work is out of scope per spec and is only captured as a documentation note
  in Phase 4 (FR-05).

## Dependencies
None. Self-contained within the `pte-api` monorepo — no external service,
CI, or docker-compose changes required (confirmed clean by research).

## Risks
- HIGH: Postgres native enum type mismatch on service startup after removing
  Flyway-created schema as the source of truth — mitigated by the dedicated
  Phase 1 static audit plus a Phase 3 live-startup verification as a runtime
  safety net.
- MEDIUM: A service is missed during the 9x batched removal in Phase 2 —
  mitigated by a final repo-wide `grep -i flyway` sweep as an explicit Phase
  2 step before moving on.
- MEDIUM: `ddl-auto=update` later leaks into a production/staging profile —
  mitigated by documenting the production/staging boundary explicitly in
  Phase 4 (NFR requirement); no prod/staging profile exists yet in any
  service's config today.
- LOW: Developers forget the one-time manual local step (dropping
  `flyway_schema_history` / resetting their local DB) and hit confusing
  startup errors — mitigated by documenting it clearly in Phase 4 as a
  per-developer manual step (explicitly not automated, per spec).
- LOW: Deleted `db/migration` SQL loses schema-intent comments — mitigated by
  Phase 1 audit capturing anything schema-relevant beforehand, and git
  history retaining the deleted files regardless.
- LOW: Phase 1's audit record had no fixed storage location, making
  completeness hard to verify after the fact — resolved by pinning it to a
  `## Audit Results` section appended directly in `phase-01-enum-schema-audit.md`.
- LOW: No explicit rollback path is called out if Phase 3 verification fails
  across several services at once — acceptable given the change is
  git-tracked, dev-only, and touches no shared environment; if it happens,
  the first response should be reverting the branch (restoring `db/migration`
  + the Flyway deps for the affected service) before further debugging,
  rather than patching forward under pressure.

## Red-Team Review
Reviewed by `plan-reviewer` (verdict: WARN). 2 HIGH findings ACCEPTED:
- Missing index recreation: resolved by user decision to fully mirror all 30
  `CREATE INDEX` statements as `@Index` annotations during Phase 2 itself
  (Phase 1 inventories them, Phase 2 applies them) — no indexes are lost,
  nothing deferred to a future production baseline.
- Undocumented `ddl-auto=update` limitations (NOT NULL on non-empty tables,
  no drop/rename): folded into Phase 4's README section.

2 NOTED findings folded into the Risks list above. 1 finding (FK
`ON DELETE CASCADE` loss) was investigated and REJECTED: every parent entity
with a cascading child already declares `@OneToMany(cascade = CascadeType.ALL,
orphanRemoval = true)` and no `@Modifying`/native bulk-delete query bypasses
the JPA entity graph, so application-level cascade already covers what the
DB-level constraint provided — not a real regression.

## Validation Decisions
- Missing indexes (30 total): mirror as `@Index` annotations in Phase 2, not
  deferred — see phase-02, step 6.
- Phase 3 live verification (service startup, entity-change trial): manual,
  run by the project owner after cook finishes — not automated via script or
  docker-compose, consistent with the single-developer-local-DB context.

## Session Notes
<!-- Updated by cook automatically — do not edit manually -->

**Last active:** 2026-08-04 (cook session)
**Phase in progress:** none — all 4 plan phases complete
**Status:** All plan phases done. Proceeding to ck:cook pipeline Step 3
(tester) → Step 4 (code-reviewer, --hard: no auto-approve) → Step 5
(finalize: project-manager, docs-manager, git-manager).

### Decisions made this session
- Actual index count is 32, not the 30 estimated during planning — full
  inventory with table/column detail written to phase-01's `## Audit
  Results` section; all 32 mirrored as `@Index` in Phase 2.
- Confirmed zero native Postgres enums and all `@Enumerated` mappings use
  `EnumType.STRING` consistent with `VARCHAR` columns — no entity fixes
  needed in Phase 2.
- Composite index `idx_proctor_sessions_session_proctor` mirrored as a single
  `@Index(columnList = "session_public_id, proctor_public_id")` (comma-separated
  column list, matching the original two-column SQL index).
- `user_directory_roles` index mirrored via `@CollectionTable(indexes = ...)`
  since that table backs an `@ElementCollection`, not a standalone `@Entity`.
- Deleted `src/main/resources/db/migration` from all 9 services; the
  `target/classes/db/migration` copies were stale build output, cleared by
  `mvn clean install`.
- `mvn clean install` (all 13 modules): BUILD SUCCESS, full existing test
  suite green, confirming FR-04 held under real execution.
- User asked to run Phase 3's steps 4–6 via Docker instead of doing it by
  hand — cook started `pte-postgres`/`pte-rabbitmq` via the project's
  existing `docker-compose.yml` and ran the `admin` service directly.
  Confirmed schema auto-creation (matching the deleted V1+V2 migrations
  exactly) and the live entity-change trial, both against the real
  container. Full details in phase-03.md.
- Found and worked around a local-only JVM/Postgres timezone mismatch
  (`Asia/Saigon` vs `Asia/Ho_Chi_Minh`) — not an application change, just a
  launch flag for this verification run.
- Found a separate, pre-existing, out-of-scope issue: `admin`'s full
  application context fails after the JPA layer initializes, because
  `OutboxWriter` needs an `ObjectMapper` bean not available when running the
  jar standalone this way. Unrelated to Flyway/JPA/ddl-auto — flagged for
  the project owner, not fixed here.

- Created `pte-api/README.md` (didn't exist before) with the 4 required
  sections: re-add trigger, one-time local reset step, prod/staging
  boundary, and `ddl-auto=update` limitations — cross-checked against spec
  FR-05 and the NFR wording.

### Next immediate action
Awaiting user approval at the Phase 4 review gate (--hard mode requires
explicit approval) before proceeding to the cook pipeline's Step 3 (tester).
