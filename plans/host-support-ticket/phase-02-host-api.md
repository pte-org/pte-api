# Phase 2: Host API

## Requirements
HOST_ADMIN can submit a support ticket, list their own tenant's tickets with optional filters and pagination, and retrieve full ticket detail including all admin notes in chronological order — all scoped to the caller's tenant.

## Steps
1. Define `SupportConstants` with all error codes (`TICKET_NOT_FOUND`, `INVALID_ENTITY_REFERENCE`, `ENTITY_REFERENCE_NOT_FOUND`, `INVALID_TICKET_ENTITY_PAIR`) and audit event names (`AGGREGATE_SUPPORT_TICKET`, `EVENT_TICKET_SUBMITTED`). Create exception classes for each error code extending `DomainException` with the appropriate HTTP status (404 for not-found, 400 for validation failures).

2. Create `EntityReferenceValidator` service: for a given `TicketEntityType` + `entityId` pair, call the corresponding module facade (`QuestionModuleService`, session, or attempt) to confirm the entity exists **AND belongs to the caller's tenant** (`entity.tenantId == null OR entity.tenantId == caller.tenantId()`). Throw `EntityReferenceNotFoundException` (404) if not found or if the entity is owned by a different tenant (never reveal cross-tenant existence). Return early if both fields are null (valid unlinked ticket). Throw `InvalidTicketEntityPairException` (400) if exactly one of the pair is null. Note: platform-level entities (e.g., questions with `tenantId = null`) are accessible to all tenants.

3. Create repository interfaces: `SupportTicketRepository` with a paginated query by `(tenantId, status, category)` where status and category are optional filters; `SupportTicketNoteRepository` with a `findByTicketPublicId` ordered by `createdAt ASC`.

4. Create `SupportTicketMapper` converting `SupportTicket` + `List<SupportTicketNote>` → `SupportTicketResponse` and `SupportTicket` alone → a summary variant `SupportTicketSummaryResponse` (no notes) used in list results.

5. Create `SupportTicketService` with three host-facing methods: `submit` (validates entity pair, calls validator, saves ticket with `tenantId` and `submitterUserPublicId` from `caller`), `listForHost` (queries by `caller.tenantId()` with optional status/category filters, page/size capped at 100), and `getDetailForHost` (loads by `publicId`, checks `tenantId` matches `caller.tenantId()` or throws `SupportTicketNotFoundException`).

6. Create request DTOs with validation annotations: `SubmitTicketRequest` (`category` not null, `description` 1–2000 chars, nullable `entityType` + `entityId` pair). Create response records: `SupportTicketNoteResponse` (`publicId`, `adminPublicId`, `content`, `createdAt`) and `SupportTicketResponse` (`publicId`, `tenantId`, `submitterUserPublicId`, `category`, `description`, `status`, `entityType`, `entityId`, `createdAt`, `updatedAt`, `notes`).

7. Create `HostSupportTicketController` under `internal/controller/` with class-level `@PreAuthorize("hasRole('HOST_ADMIN')")`. Expose `POST /api/v1/support-tickets` (submit), `GET /api/v1/support-tickets` (list with `?status=&category=&page=&size=`), `GET /api/v1/support-tickets/{id}` (detail). Each handler resolves `CurrentUser` via `CurrentUserContext.required()`.

## Files to Create or Modify

All paths relative to `d:\FPT\9thSemester\pte\pte-api\app\src\main\java\com\pte\support\`:

| File | Action |
|---|---|
| `internal/constant/SupportConstants.java` | Create |
| `internal/exception/SupportTicketNotFoundException.java` | Create |
| `internal/exception/InvalidTicketEntityPairException.java` | Create |
| `internal/exception/EntityReferenceNotFoundException.java` | Create |
| `internal/exception/InvalidStatusTransitionException.java` | Create |
| `internal/service/EntityReferenceValidator.java` | Create |
| `internal/repository/SupportTicketRepository.java` | Create |
| `internal/repository/SupportTicketNoteRepository.java` | Create |
| `internal/mapper/SupportTicketMapper.java` | Create |
| `internal/dto/request/SubmitTicketRequest.java` | Create |
| `internal/dto/response/SupportTicketNoteResponse.java` | Create |
| `internal/dto/response/SupportTicketSummaryResponse.java` | Create |
| `internal/dto/response/SupportTicketResponse.java` | Create |
| `internal/service/SupportTicketService.java` | Create |
| `internal/controller/HostSupportTicketController.java` | Create |

## Success Criteria
- `POST /api/v1/support-tickets` with valid body returns 200, ticket has `status = OPEN`, and appears in the subsequent `GET /api/v1/support-tickets` for the same tenant.
- `GET /api/v1/support-tickets` returns only tickets belonging to `caller.tenantId()`; a second tenant's tickets are never included.
- `GET /api/v1/support-tickets/{id}` for a ticket from a different tenant returns 404.
- `POST /api/v1/support-tickets` with `entityType = QUESTION` but `entityId = null` returns 400.
- `POST /api/v1/support-tickets` with `entityType = QUESTION` and a non-existent `entityId` returns 404.
- `GET /api/v1/support-tickets/{id}` on a ticket with notes returns all notes ordered by `createdAt ASC`.

## Acceptance Criteria Mapping
- P1: HOST_ADMIN submit ticket linked to entity → FR-01, FR-07.
- P1: HOST_ADMIN list own tickets with status visible → FR-02.
- P2: HOST_ADMIN sees admin notes on resolved ticket via detail endpoint → FR-03, FR-06 (read side).

## Risks
- Cross-module validators may not yet expose existence-check methods: resolve before starting this phase by reviewing `QuestionModuleService`, `ExamSessionModuleService`, `AttemptModuleService`; add stub methods if missing.
- `description` stored as VARCHAR(2000) — ensure migration column length matches the DTO `@Size` validation limit.

### Tests to Write

**`app/src/test/java/com/pte/support/internal/service/SupportTicketServiceTest.java`** — `@ExtendWith(MockitoExtension.class)`:

| Test name | What it verifies |
|---|---|
| `submit_validUnlinkedTicket_savesWithOpenStatus` | Happy path: no entity ref, ticket saved with OPEN |
| `submit_validLinkedToQuestion_validatesAndSaves` | Entity ref validator called; ticket saved with entityType/entityId |
| `submit_entityIdNullEntityTypeSet_throwsInvalidPair` | Validator throws `InvalidTicketEntityPairException` |
| `submit_entityTypeNullEntityIdSet_throwsInvalidPair` | Validator throws `InvalidTicketEntityPairException` |
| `submit_entityDoesNotExist_throwsEntityReferenceNotFound` | Validator throws `EntityReferenceNotFoundException` |
| `listForHost_returnsOnlyCallerTenantTickets` | Query is scoped to `caller.tenantId()` |
| `listForHost_filterByStatus_passesStatusToRepository` | Status filter forwarded to repository |
| `getDetailForHost_differentTenantTicket_throwsNotFound` | Loaded ticket's tenantId != caller's → `SupportTicketNotFoundException` |
| `getDetailForHost_ownTicket_returnsWithNotes` | Notes fetched and included ordered by createdAt |

**`app/src/test/java/com/pte/support/internal/service/SupportTicketTenantIsolationTest.java`**:

| Test name | What it verifies |
|---|---|
| `listForHost_neverReturnsCrossTenantsTickets` | Two tenants' tickets in repo; host A only sees tenant A's tickets |
| `getDetailForHost_crossTenantAccess_alwaysNotFound` | Direct lookup by publicId for another tenant's ticket → 404, not 403 |

**`app/src/test/java/com/pte/support/internal/mapper/SupportTicketMapperTest.java`**:

| Test name | What it verifies |
|---|---|
| `toResponse_mapsAllFields` | All fields including notes list correctly mapped |
| `toSummaryResponse_excludesNotes` | Summary variant contains no notes field |

## Execution Log

### Errors Encountered
- None

### Root Cause
- N/A

### Resolution
- N/A

### Test Results After Fix
- `.\mvnw test -pl app -Dtest="SupportTicketServiceTest,SupportTicketMapperTest"` → 9 tests, 0 failures — BUILD SUCCESS
- Full suite: 949 tests, 11 pre-existing failures in `AttemptLifecycleCapabilityTest` (unrelated to this phase; pre-date this work)
