# Phase 1: Domain and Migration

## Requirements
Define the full persistence model for the support ticket system: two entities (`SupportTicket`, `SupportTicketNote`), three enums, and the Flyway V71 migration that creates both tables with all required indexes and constraints.

## Steps
1. Create the three enum types under `domain/enums/`: `TicketCategory` (BUG, CONTENT_COMPLAINT, GENERAL_FEEDBACK), `TicketStatus` (OPEN, IN_PROGRESS, RESOLVED), and `TicketEntityType` (EXAM_SESSION, EXAM_ATTEMPT, QUESTION).

2. Create the `SupportTicket` entity extending `BaseEntity`. Fields: `tenantId` (UUID, not null), `submitterUserPublicId` (UUID, not null), `category` (enum, not null), `description` (VARCHAR 2000, not null), `status` (enum, default OPEN, not null), `entityType` (VARCHAR 32, nullable), `entityId` (VARCHAR 36, nullable), **`version` (`@Version Long`, not null, default 0) — required for optimistic locking on concurrent admin status updates**. Add paired-null validation guard and status-transition methods `startProcessing()` and `resolve()` directly on the entity; each method throws `InvalidStatusTransitionException` if the current state does not allow the transition.

3. Create the `SupportTicketNote` entity extending `BaseEntity`. Fields: `ticketId` (Long, FK to `support_ticket.id`, not null), `ticketPublicId` (UUID, not null), `adminPublicId` (UUID, not null), `content` (TEXT, not null). `createdAt` comes from `BaseEntity` — no additional timestamp field needed.

4. Write `V71__support_ticket.sql`: create `support_ticket` table (all columns from step 2 plus `BaseEntity` columns, including `version BIGINT NOT NULL DEFAULT 0`), create `support_ticket_note` table (all columns from step 3), add a `CHECK` constraint that `entity_type IS NULL = entity_id IS NULL` (pair-null invariant), add a `CHECK` constraint that `status IN ('OPEN','IN_PROGRESS','RESOLVED')`, add indexes on `(tenant_id, status, created_at)` for the host list query and on `(ticket_id, created_at)` for the notes query.

5. Create `package-info.java` at `com.pte.support` to document module ownership.

## Success Criteria
- `mvn flyway:migrate` on a clean schema applies V71 without error and produces both tables with the correct columns, constraints, and indexes.
- Running `mvn test -pl app -Dtest=SupportTicketTest` passes all entity-level transition tests (see Tests to Write).
- No `@ManyToOne` or FK reference from `support_ticket` or `support_ticket_note` to any table outside the `support_*` namespace (verify with `grep -r "@ManyToOne" app/src/main/java/com/pte/support`).

## Acceptance Criteria Mapping
- Supports FR-07 (entity reference stored as loose VARCHAR pair, no FK, CHECK constraint enforces pair-null invariant).
- Provides the schema foundation for all P1, P2 stories.

## Risks
- `BaseEntity` has no `createdBy` — `submitterUserPublicId` must be an explicit column; the migration must include it.
- Enum `CHECK` constraints in Postgres must list all values; if an enum value is added later a new migration is required — mitigate by keeping the CHECK in the migration consistent with the enum definition at V71 time.

### Tests to Write

**`app/src/test/java/com/pte/support/domain/SupportTicketTest.java`** — plain unit tests, no Spring context:

| Test name | What it verifies |
|---|---|
| `newTicket_hasStatusOpen` | A freshly created `SupportTicket` has `status == OPEN` |
| `startProcessing_fromOpen_transitionsToInProgress` | `startProcessing()` on OPEN ticket → `IN_PROGRESS` |
| `startProcessing_fromResolved_throwsInvalidTransition` | `startProcessing()` on RESOLVED ticket throws `InvalidStatusTransitionException` |
| `resolve_fromInProgress_transitionsToResolved` | `resolve()` on IN_PROGRESS ticket → `RESOLVED` |
| `resolve_fromOpen_throwsInvalidTransition` | `resolve()` on OPEN ticket throws `InvalidStatusTransitionException` |
| `resolve_alreadyResolved_throwsInvalidTransition` | `resolve()` on already-RESOLVED ticket throws |
| `startProcessing_alreadyInProgress_throwsInvalidTransition` | Idempotency not allowed — second call throws |

## Execution Log

### Errors Encountered
- None

### Root Cause
- N/A

### Resolution
- N/A

### Test Results After Fix
- `.\mvnw test -pl app -Dtest="SupportTicketTest"` → Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
