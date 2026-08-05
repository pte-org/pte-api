# Phase 2: Outbox Writer Family

Covers user stories: P1 (all 9 services start, dependency chain rooted in
`AbstractOutboxWriter` from Phase 1), P1 (single consistent Jackson
version), P2 (equivalent error-handling behavior). Implements FR-01 (the 8
per-service subclasses).

## Requirements
All 8 services with a per-service `OutboxWriter` (admin, authoring,
exam-delivery, iam, proctor, reporting, scheduling, scoring — `notification`
and `media` have no `OutboxWriter` and are out of scope for this phase) and
the one outbox-adjacent business service (`SnapshotPublishService` in
authoring) inject the Jackson 3 `JsonMapper` instead of the Jackson 2
`ObjectMapper`, and compile/serialize consistently with the shared bean added
in Phase 1.

## Steps
1. Confirm Phase 1 is merged and `pte-common` builds clean before starting —
   every file in this phase depends on it.
2. In each of the 8 `OutboxWriter.java` files, change the constructor
   parameter from the Jackson 2 `ObjectMapper` to the Jackson 3 `JsonMapper`
   and update the import; the `super(...)` call to `AbstractOutboxWriter`
   stays structurally the same since Phase 1 already changed the parent's
   expected type.
3. In `SnapshotPublishService` (authoring), change its directly-injected
   `ObjectMapper` field to `JsonMapper` and review its own JSON handling (the
   deep-copy of question options into the immutable snapshot) for any
   `JsonProcessingException` catch that needs the same deliberate
   checked-to-unchecked review as Phase 1's.
4. Apply this same mechanical edit identically across all 8 `OutboxWriter`
   files — this is one repeated pattern per service, not 8 separate design
   decisions.
5. For each of the 9 files touched in this phase, re-check whether any
   `throws IOException` or `catch (JsonProcessingException ...)` exists
   beyond the constructor injection itself, and give each one an explicit
   keep/remove decision consistent with Phase 1's approach — never a silent
   deletion.
6. Build every affected service module (`mvn -pl services/<name> install`
   for each of the 8) to confirm each compiles clean against the new type
   before moving to Phase 3.
7. Run a repo-wide grep limited to `messaging/outbox/OutboxWriter.java` and
   `SnapshotPublishService.java` paths to confirm zero remaining Jackson 2
   `ObjectMapper` references across this phase's file set.

## Success Criteria
- All 8 `OutboxWriter.java` files and `SnapshotPublishService.java` import
  `tools.jackson.databind.json.JsonMapper`, not
  `com.fasterxml.jackson.databind.ObjectMapper`.
- Each of the 8 affected service modules builds clean in isolation.
- Every `JsonProcessingException`/`IOException` touch point identified in
  step 5 has a recorded, deliberate decision (kept with explicit handling,
  or confirmed safe to drop) rather than being silently removed.
- The Phase 2 grep sweep (step 7) returns zero matches for the old Jackson 2
  import across this phase's files.

## Risks
- A service's `OutboxWriter` is migrated but its surrounding service (like
  `SnapshotPublishService`) still injects the old Jackson 2 `ObjectMapper`
  independently, leaving that one service's module in a broken half-migrated
  state even though the outbox path itself compiles: mitigated by treating
  `SnapshotPublishService` as an explicit, named file in this phase rather
  than assuming "outbox family" only means files literally named
  `OutboxWriter.java`.
- Deploying this phase's services independently of Phase 3's consumers before
  the full fleet is migrated: mitigated by the plan's overall big-bang
  constraint — this phase's changes are not deployed standalone; deployment
  only happens after Phase 4's full-repo verification.
