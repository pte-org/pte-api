# Phase 1: JVM & Infra UTC Enforcement

Covers user stories: P1 ("run `java -jar` on any OS timezone without a
`TimeZone` connection error"). Implements FR-01, FR-03.

## Requirements
Every Postgres-connected service's JVM reports `UTC` as its default
timezone before any database connection opens, independent of the host
OS's configured timezone; the `postgres` container declares `TZ: UTC`
explicitly; and the one cron-based scheduled job whose real-world fire time
depends on the JVM default zone is either pinned to `UTC` or has its new
wall-clock trigger time deliberately documented.

## Steps
1. Add `TimeZone.setDefault(TimeZone.getTimeZone("UTC"))` as the literal
   first line of `main()`, before `SpringApplication.run(...)`, in all 10
   Postgres-connected services' `*Application.java` (admin, authoring,
   exam-delivery, iam, media, notification, proctor, reporting, scheduling,
   scoring). Exclude `gateway` — it declares no `datasource`/`postgresql`
   dependency and never opens a Postgres connection.
2. Add `environment: TZ: UTC` to the `postgres` service block in
   `docker-compose.yml`, alongside the existing `POSTGRES_DB` /
   `POSTGRES_USER` / `POSTGRES_PASSWORD` entries.
3. Audit every `@Scheduled` usage in `pte-common`'s messaging package:
   confirm `AbstractOutboxCleanupJob`'s `cron = "${pte.outbox.cleanup-cron:0
   0 3 * * *}"` has no `zone` attribute (zone-sensitive, needs a decision),
   and confirm `AbstractOutboxRelay`'s `fixedDelayString = "..."` is a fixed
   interval, not a wall-clock trigger (not zone-sensitive, needs no change)
   — do not apply the fix to both classes on the assumption they behave the
   same way.
4. Decide and apply the cron fix for `AbstractOutboxCleanupJob`: add
   `zone = "UTC"` to the `@Scheduled` annotation. **This is a deliberate
   real-world behavior change, not a no-op**: today, with no `zone`
   attribute, the cron interprets `0 0 3 * * *` in the JVM's current default
   zone (Vietnam-local), so it actually fires at ~3am Vietnam time. Once
   Phase 1 step 1 lands (`TimeZone.setDefault(UTC)`), an unpinned cron would
   silently start firing at 3am UTC = 10am Vietnam instead — an
   unintentional 7-hour shift. Adding `zone = "UTC"` pins the intent
   explicitly, but the resulting job now runs at 10am Vietnam time going
   forward, not 3am. Record this new effective local time in a one-line code
   comment above the annotation (or the service README) so anyone later
   wondering why outbox cleanup moved from 3am to 10am Vietnam has an answer.
5. Document `-Duser.timezone=UTC` as a recommended (not required) JVM launch
   argument in any existing run command/script/README for local
   development, as a belt-and-suspenders redundant safety net alongside the
   code-level fix from step 1.
6. Start each of the 10 affected services standalone against the local
   `postgres:17` container and confirm each connects successfully with zero
   `FATAL: invalid value for parameter "TimeZone"` errors in the startup
   log.
7. Run a final repo-wide grep for `TimeZone.setDefault` across all
   `*Application.java` files to confirm all 10 targeted services (and no
   others) were edited, catching any service missed in the batch.

## Success Criteria
- All 10 targeted `*Application.java` files contain
  `TimeZone.setDefault(TimeZone.getTimeZone("UTC"))` as the first statement
  in `main()`; `GatewayApplication.java` is unchanged.
- `docker-compose.yml`'s `postgres` service `environment` block contains
  `TZ: UTC`.
- `AbstractOutboxCleanupJob`'s `@Scheduled` annotation contains `zone =
  "UTC"`; `AbstractOutboxRelay`'s `@Scheduled` annotation is unchanged
  (fixed-delay, not cron).
- Starting each of the 10 services standalone against the local Postgres
  container produces zero `invalid value for parameter "TimeZone"` errors
  in the log.

## Risks
- Cron-zone fix applied to the wrong class (or both, unnecessarily):
  mitigated by step 3's explicit audit distinguishing cron-based
  (`AbstractOutboxCleanupJob`) from fixed-delay-based (`AbstractOutboxRelay`)
  scheduling before any annotation is edited.
- A service missed during the 10x batched edit: mitigated by the mandatory
  repo-wide grep sweep in step 7 before moving to Phase 2.
- `docker-compose.yml`'s `TZ: UTC` addition requires a container recreate
  (not just restart) to take effect, and is silently skipped: mitigated by
  verifying against a freshly recreated `pte-postgres` container in step 6,
  not a container left running from before this change.
