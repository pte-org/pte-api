# Phase 3: JSON Compatibility Audit & Fleet-Wide Verification

Covers user stories: P1 (both P1 stories fully realized end-to-end), P2
(`docker-compose.yml` `TZ: UTC` verified live). Implements FR-05 and the
plan's Non-Functional Requirements (correctness, portability).

## Requirements
No RabbitMQ consumer, event producer, or REST API layer in `pte-api` breaks
on the ISO-8601 `Z`-suffixed JSON format `Instant` produces (vs.
`LocalDateTime`'s no-offset format), and the full fleet builds and starts
cleanly with zero `TimeZone` connection errors, confirming Phases 1–2 hold
together end-to-end.

## Steps
1. Check the status of the separate, in-progress `jackson3-objectmapper
   -migration` plan — its Phase 1 pins `Instant`'s JSON (de)serialization
   format under the new `JsonMapper`. If it has landed, audit against the
   live pinned format; if not, note the pending format-pinning change as an
   explicit caveat rather than treating today's Jackson 2 default as final.
2. Grep every `@RabbitListener` consumer and any REST controller/DTO across
   all 10 services for hardcoded date parsing (e.g. a `DateTimeFormatter`
   pattern or manual string-splitting) that assumes the old no-offset
   `LocalDateTime` format. This session's planning-time grep found zero such
   hardcoded formatters — re-confirm at execution time.
3. For each RabbitMQ producer/consumer pair still communicating on a shared
   event/DTO type, confirm both sides deserialize the `Z`-suffixed `Instant`
   format correctly (self-consistent within `pte-api`, since both ends
   already declare `Instant` fields on the shared event records).
4. Confirm no `pte-api` REST response DTO exposing a timestamp field still
   declares `LocalDateTime`/`OffsetDateTime` (should already be covered by
   Phase 2's grep, but re-check DTOs specifically since they're a distinct
   file set from entities).
5. Run `mvn clean install` across all 12 Maven modules (`pte-common`,
   `gateway`, and the 10 services) and confirm a full green build.
6. Start all 10 Postgres-connected services standalone (or via
   `docker-compose`) against the UTC-configured local Postgres container and
   confirm zero `TimeZone` connection errors and zero JSON
   deserialization errors on startup or first request.
7. Before checking anything off, **correct `spec.md`'s stale figures first**:
   its Success Criteria/User Stories text still says "9 service," "12 entity
   đã đổi," and "13 module" — all three are now known-wrong (10 services, 0
   entity conversions needed since all 51 entities already used `Instant`,
   12 modules). Add a dated addendum note in `spec.md` correcting these
   numbers to what was actually true and verified, so the historical record
   isn't misleading (e.g. don't check off "12 entity đã đổi" when literally
   zero were converted).
8. Update the plan's Success Criteria checklist in `spec.md` (or the
   equivalent tracking doc) marking each item verified against the
   corrected figures from step 7, with the concrete command/output that
   confirmed it.

## Success Criteria
- `mvn clean install` is green across all 12 modules.
- All 10 Postgres-connected services start standalone against the UTC
  Postgres container with zero `invalid value for parameter "TimeZone"`
  errors.
- No hardcoded no-offset date parsing found in any `@RabbitListener`
  consumer or REST DTO across `pte-api` (or, if found, fixed and
  re-verified).
- Every item in `spec.md`'s Success Criteria checklist is checked off with
  its verifying evidence.

## Risks
- `jackson3-objectmapper-migration` lands between this audit and actual
  deployment, changing the live JSON format after the audit already passed:
  mitigated by step 1's explicit status check and the plan.md Dependencies
  note to re-run this audit if that plan lands afterward.
- A consumer outside `pte-api` (frontend, another repo) parses the old
  no-offset format and isn't caught by this audit, since it's out of this
  repo's scope: mitigated by flagging this explicitly as a known gap here
  rather than silently assuming full coverage — out of scope per spec, but
  worth a heads-up to the project owner before rollout.
- A service fails to start for a reason unrelated to timezone (e.g. an
  unrelated missing bean, as previously found during
  `remove-flyway-hibernate-only` verification) and gets misattributed to
  this plan's changes: mitigated by reading the actual startup error message
  before assuming it's timezone-related.
