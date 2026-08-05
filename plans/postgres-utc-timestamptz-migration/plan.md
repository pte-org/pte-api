# Plan: Postgres/JVM UTC Standardization (`timestamptz` migration)
Status: ✅ Complete — implementation, test, code-review PASSED
Date: 2026-08-05
Mode: Hard

## Overview
Standardizes the whole `pte-api` fleet on UTC — JVM default timezone, the
local Postgres container, and every entity's timestamp column type — so the
`Asia/Saigon`/`Asia/Ho_Chi_Minh` alias mismatch that breaks PgJDBC's
`TimeZone` connection parameter is eliminated permanently, not just
worked around for one machine, per `spec.md`.

## Phases
- [x] Phase 1: JVM & Infra UTC Enforcement — `TimeZone.setDefault(UTC)` in
      all 10 service `main()` classes, `TZ: UTC` on the `docker-compose.yml`
      `postgres` service, and an explicit `zone = "UTC"` audit/fix for the
      cron-based `@Scheduled` job in `pte-common`.
- [x] Phase 2: Entity Timestamp Verification & Local DB Reset — re-verify no
      entity still uses `LocalDateTime`/`OffsetDateTime`, convert any found,
      then drop/recreate each dev's local Postgres DB so `ddl-auto=update`
      materializes fresh `timestamptz` columns from `Instant` fields.
- [x] Phase 3: JSON Compatibility Audit & Fleet-Wide Verification — audit
      RabbitMQ/API consumers for hardcoded no-offset date parsing, then
      `mvn clean install` + live startup of all services to confirm zero
      `TimeZone` connection errors.

## Research Summary
Two researcher passes (primary + alternative) informed this plan, plus a
planning-time re-verification pass (following the spec's own stated
assumption to re-check the "12 entities" figure at plan time, the same
practice this repo already used in `jackson3-objectmapper-migration`) that
found a significant discrepancy against the original brainstorm estimate,
worth flagging explicitly:

- **Primary researcher — `TimeZone.setDefault()` ordering is safe:**
  `TimeZone.setDefault(TimeZone.getTimeZone("UTC"))` as the literal first
  line of each service's `main()` runs before `SpringApplication.run(...)`
  constructs any `ApplicationContext`/`DataSource` bean, so it is guaranteed
  to take effect before the first Postgres connection is opened. All main
  classes currently share an identical minimal bootstrap (`IamApplication`
  confirmed representative) — a clean, uniform insertion point.
- **Primary researcher — belt-and-suspenders JVM flag:** also documenting
  `-Duser.timezone=UTC` as a recommended (not required) launch arg in any
  run script/README is a redundant safety net on top of the code-level fix,
  not a replacement for it.
- **Primary researcher — `Instant` + plain `@Column` is correct, zero-config
  Hibernate 6:** Hibernate 6.x's default `JdbcType` for `Instant` already
  maps to `timestamptz`. No `@JdbcTypeCode` or
  `hibernate.jdbc.time_zone` property is needed for `Instant` fields
  specifically.
- **Primary researcher — full DB drop/recreate over `ALTER COLUMN`:** matches
  the team's own precedent from `remove-flyway-hibernate-only` (dev-only, one
  Postgres instance per developer, no shared data) — hand-rolled
  `ALTER COLUMN ... USING col AT TIME ZONE 'utc'` is a real pitfall if any
  existing naive timestamp wasn't actually already UTC.
- **Primary researcher — JSON format gotcha (FR-05):** `Instant` serializes
  as an ISO-8601 `Z`-suffixed string vs. `LocalDateTime`'s no-offset string;
  any consumer doing its own date parsing of the old format needs auditing.
  This interacts directly with the separate, currently in-progress
  `jackson3-objectmapper-migration` plan — see Dependencies below.
- **Alternative researcher — confirms the env-var-only fix was correctly
  rejected:** `timestamp without time zone` relies on every future
  reader/writer independently honoring a UTC convention by discipline;
  `timestamptz` enforces it at the type level instead. Dev-only phase (no
  production data yet) is the cheapest possible window to pay this
  conversion cost, same reasoning already used to justify removing Flyway.
- **Alternative researcher — cron zone side effect (folded into Phase 1 as an
  explicit step, not left as a risk-only footnote):** `pte-common`'s
  `AbstractOutboxCleanupJob` uses `@Scheduled(cron = "...")` with no `zone`
  attribute, defaulting to the JVM's default zone. Once
  `TimeZone.setDefault(UTC)` lands fleet-wide, its real-world fire time
  silently shifts (3am was intended in Vietnam local time; without a `zone`
  attribute it becomes 3am UTC = 10am Vietnam time).
- **This session's re-verification finding (supersedes the original "12
  entities" estimate):** a fresh repo-wide grep for `LocalDateTime` /
  `OffsetDateTime` across all `.java` files under `pte-api` returned **zero**
  matches in source code — every domain entity (51 `@Entity` classes
  checked) already extends `BaseEntity` (`Instant createdAt`/`updatedAt`) or
  declares its own timestamp fields as `Instant` (e.g. `ScoringAnswer
  .scoredAt`, `NotificationLog.sentAt`, `RefreshToken.expiresAt`,
  `ProctorSession.openedAt/closedAt`, `ExamAttempt.startedAt/submittedAt`,
  `ExamSession.opensAt/closesAt`, `AttemptReport.publishedAt`,
  `TimerState.taskStartedAt/prepDeadline/responseDeadline`). FR-02's entity
  type migration therefore appears **already complete in the current
  codebase** — Phase 2 is re-scoped from "convert 12 entities" to
  "re-verify + reset the local DB schema to materialize `timestamptz`",
  keeping the verification step mandatory (not assumed) since it must be
  re-run again at execution time in case new fields landed since this
  planning pass.
- **This session's finding — not every `@Scheduled` use is zone-sensitive:**
  `AbstractOutboxRelay.poll()` uses `@Scheduled(fixedDelayString = ...)`
  (a fixed interval, no wall-clock trigger time), which is **not** affected
  by a JVM default-zone change — only cron-expression-based `@Scheduled`
  triggers are. Only `AbstractOutboxCleanupJob`'s cron job needs the explicit
  `zone` fix; `AbstractOutboxRelay` needs no change. Phase 1 states this
  distinction explicitly so the fix isn't misapplied to the wrong class.
- **This session's finding — 10 service modules, not 9:** the root
  `pte-api/pom.xml` lists 10 `services/*` modules (admin, authoring,
  exam-delivery, iam, media, notification, proctor, reporting, scheduling,
  scoring) plus `pte-common` and `gateway` (12 modules total). The spec's
  "9 services" figure is a stale count also present in the prior
  `remove-flyway-hibernate-only` plan. This plan targets all 10 Postgres-
  connected service `main()` classes explicitly by name; `gateway` is
  excluded because it declares no `datasource`/`postgresql` dependency
  anywhere and never opens a Postgres connection.

## Dependencies
- **`jackson3-objectmapper-migration`** (status: In Progress, same repo) —
  that plan's Phase 1 adds a shared bean pinning `Instant`'s JSON
  (de)serialization format under the new `JsonMapper`. Phase 3's FR-05 audit
  in this plan should be run against whichever `ObjectMapper`/`JsonMapper`
  is actually active at execution time; if `jackson3-objectmapper-migration`
  has not yet landed, note the pending format-pinning change as a caveat in
  the audit rather than treating today's Jackson 2 default as final.
- No other external service, CI, or shared-environment dependency (dev-only,
  one Postgres instance per developer, consistent with
  `remove-flyway-hibernate-only`'s established assumption).

## Risks
- HIGH: The cron-zone side effect (`AbstractOutboxCleanupJob`) ships
  unaudited, silently shifting outbox cleanup's real-world fire time by the
  JVM-vs-Vietnam offset — mitigated by making it an explicit, visible Phase 1
  step (not a risk-only footnote), with an explicit decision recorded either
  way (add `zone = "UTC"` or deliberately document the new wall-clock time).
- MEDIUM: A service's `main()` is missed during the 10x batched edit in
  Phase 1 — mitigated by a final repo-wide grep sweep confirming
  `TimeZone.setDefault` appears in all 10 targeted files before moving on.
- MEDIUM: `jackson3-objectmapper-migration` lands after this plan's Phase 3
  audit runs, silently changing the live JSON format the audit validated
  against — mitigated by explicitly checking that plan's status before
  Phase 3, and re-running the audit if it lands in between.
- MEDIUM: Phase 2's re-verification grep finds stray `LocalDateTime`/
  `OffsetDateTime` usage that this planning pass's grep missed (e.g. a field
  added between planning and execution) — mitigated by keeping the grep an
  explicit, mandatory Phase 2 step rather than skipping it on the assumption
  that this plan's finding still holds.
- LOW: A developer forgets the one-time local DB reset and hits a stale
  `timestamp without time zone` column mismatch — mitigated by documenting
  it explicitly in Phase 2, mirroring the same pattern already documented in
  `pte-api/README.md` for the Flyway removal.
- LOW: `-Duser.timezone=UTC` launch-flag documentation is skipped since the
  code-level fix already covers correctness — mitigated by still adding it
  to Phase 1 as a cheap, explicit belt-and-suspenders documentation step.

## Session Notes
<!-- Updated by cook automatically — do not edit manually -->

**Last active:** 2026-08-05 18:30
**Phase in progress:** none — all 3 phases complete, moving to code review
(cook Step 4)
**Status:** Implementation complete. All 3 phases done, full-repo build
green, all 10 services verified starting live twice independently (Phase 1
and Phase 3), zero `TimeZone`/bean/Jackson errors, 100% of 96 timestamp
columns confirmed `timestamptz`. Awaiting mandatory code review (--hard
mode, no auto-approve).

### Decisions made this session
- TDD: added `cleanup_scheduledAnnotation_pinsCronToUtcZone` to the existing
  `AbstractOutboxCleanupJobTest` (reflection on the `@Scheduled` annotation's
  `zone` attribute) — confirmed RED (`zone()` returned `""`) before adding
  `zone = "UTC"`, GREEN after. Did not write a bespoke unit test for the 10
  `main()` edits themselves (JVM bootstrap ordering isn't meaningfully
  unit-testable — `TimeZone.setDefault()` only proves itself by the actual
  runtime effect); relied on step 7's grep-completeness sweep + step 6's
  live standalone-startup verification instead, consistent with how
  `jackson3-objectmapper-migration`'s mechanical per-file edits were handled.
- Live verification recreated `pte-postgres` with `docker compose up -d
  --force-recreate postgres` (not just restart) since `TZ` is an env var
  baked in at container creation — confirmed `SHOW timezone;` reports
  `Etc/UTC` before starting any service.
- Started all 10 services in 3 batches (4+4+2) rather than all at once,
  after `jackson3-objectmapper-migration`'s Phase 4 verification hit a local
  machine OOM (paging file exhausted) running 10 JVMs simultaneously — same
  known local-resource constraint, not a code issue, worked around the same
  way.
- Critically, this run used **no** `-Duser.timezone` JVM flag at all (unlike
  every prior verification run in this repo, which used
  `-Duser.timezone=Asia/Ho_Chi_Minh` as a workaround) — all 10 services
  connected cleanly on the first try, confirming the code-level fix alone is
  sufficient without the flag, exactly as FR-01 requires.
- Added a "Timezone (UTC, fleet-wide)" section to `pte-api/README.md`
  documenting the code-level fix and the optional `-Duser.timezone=UTC`
  belt-and-suspenders launch flag (step 5), placed before the existing
  "Database schema management" section.

- Phase 2: re-verified zero `LocalDateTime`/`OffsetDateTime` matches
  repo-wide (confirmed, no conversion needed — matches planning-time
  finding). Dropped + recreated all 10 service databases (terminated a
  handful of leaked Hikari connections on `admin`/`reporting` first —
  leftover from earlier verification runs, not new). Started all 10
  services once each (3 batches of 4/4/2, same OOM-avoidance pattern as
  Phase 1/jackson3-objectmapper-migration) to let `ddl-auto=update`
  materialize fresh schema. Queried `information_schema.columns` across
  all 10 databases (96 timestamp columns total, not a spot sample) —
  **100% `timestamp with time zone`, zero `timestamp without time zone`**.
  Round-trip spot-check: inserted a known instant via `psql` in a UTC
  session, read it back in an `Asia/Ho_Chi_Minh` session — displayed as
  `17:30+07` with `AT TIME ZONE 'UTC'` confirming the exact same
  `10:30:00` instant, zero drift; test row deleted after.
  Documented the one-time local DB reset in `pte-api/README.md`, mirroring
  the existing Flyway-removal reset section's style.
- **Process-management finding (this session, not a code defect):** on this
  Windows/git-bash environment, `pkill -f "<jarname>.jar"` between batches
  does NOT reliably terminate the background Java processes (confirmed via
  `tasklist`/`netstat` — 13 orphaned `java.exe` processes and 3 ports still
  listening after a `pkill` that reported success). Switched to
  `taskkill //F //IM java.exe` (scoped to exclude 2 pre-existing unrelated
  java processes found listening on no service port) between batches for
  the rest of this phase — worked reliably. Worth remembering for any
  future live-verification session on this machine.

- Phase 3: confirmed `jackson3-objectmapper-migration` is
  `✅ Complete` — audited against its live pinned ISO-8601 format, not a
  caveat. Repo-wide grep for hardcoded `DateTimeFormatter`/manual date
  parsing across all `@RabbitListener` consumers and DTOs: 0 matches.
  Repo-wide `LocalDateTime`/`OffsetDateTime` re-check (2nd time this plan,
  including DTOs not just entities): still 0 matches. `mvn clean install`
  from repo root: BUILD SUCCESS, all 12 modules, full test suite green.
  Live startup of all 10 services a second time (3 batches, same
  taskkill-between-batches approach from Phase 2): all 10 `Started
  <X>Application`, 0 `TimeZone`/`NoSuchBeanDefinitionException`/Jackson
  errors — this is the first live confirmation of `jackson3-objectmapper
  -migration` and this plan's changes running together. Corrected `spec.md`'s
  stale "9 service/12 entity/13 module" figures with a dated addendum (step
  7) before checking off Success Criteria (step 8), per the plan's own
  explicit instruction not to mark a wrong number "done".

### Next immediate action
All 3 implementation phases complete. Next: mandatory code review
(cook Step 4, `--hard` mode — no auto-approve), then finalize (project-manager
/ docs-manager, cook Step 5; git-manager skipped per user's stated preference
to commit manually, same as `jackson3-objectmapper-migration`'s cook run).