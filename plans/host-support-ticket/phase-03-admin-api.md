# Phase 3: Admin API

## Requirements
PLATFORM_ADMIN can list all support tickets across every tenant with optional filters and pagination, advance a ticket through the OPEN → IN_PROGRESS → RESOLVED status flow, and append notes to any ticket — each note permanently recorded with the admin's identity and timestamp.

## Steps
1. Create request DTOs `UpdateTicketStatusRequest` (single field: target `status`, not null) and `AddNoteRequest` (`content` 1–2000 chars, not null).

2. Add `listAllForAdmin` to `SupportTicketService`: accepts optional `status`, `category`, `tenantId` filters plus `Pageable` with **page size capped at 100** (`Math.min(size, 100)` or `@PageableDefault(max = 100)`); performs a global query with no tenant scope guard; returns a paginated `SupportTicketSummaryResponse` page.

3. Add `updateStatus` to `SupportTicketService`: loads the ticket by `publicId` (no tenant guard — admin scope), calls the entity's transition method (`startProcessing()` or `resolve()` depending on the requested status), saves, and records an audit log entry. Invalid transitions propagate `InvalidStatusTransitionException` as HTTP 400.

4. Add `addNote` to `SupportTicketService`: loads the ticket by `publicId`, creates a new `SupportTicketNote` record with `adminPublicId` from `caller.userPublicId()`, `ticketId`, `ticketPublicId`, and `content`. Saves the note and records an audit log entry. The note is append-only — no update or delete path exists.

5. Extend `SupportTicketRepository` with a cross-tenant paginated query method that accepts all three optional filter parameters (`status`, `category`, `tenantId`), using Spring Data JPA `Specification` or a custom JPQL query.

6. Create `AdminSupportTicketController` under `internal/controller/` with class-level `@PreAuthorize("hasRole('PLATFORM_ADMIN')")`. Expose:
   - `GET /api/v1/admin/support-tickets` (list-all with `?status=&category=&tenantId=&page=&size=`)
   - `GET /api/v1/admin/support-tickets/{id}` (detail — loads ticket with no tenant guard, returns full `SupportTicketResponse` including all notes; required so admin can independently verify ticket content and notes)
   - `PATCH /api/v1/admin/support-tickets/{id}` (update status)
   - `POST /api/v1/admin/support-tickets/{id}/notes` (add note)
   Each handler resolves `CurrentUser` via `CurrentUserContext.required()`.

## Files to Create or Modify

All paths relative to `d:\FPT\9thSemester\pte\pte-api\app\src\main\java\com\pte\support\`:

| File | Action |
|---|---|
| `internal/dto/request/UpdateTicketStatusRequest.java` | Create |
| `internal/dto/request/AddNoteRequest.java` | Create |
| `internal/service/SupportTicketService.java` | Modify — add `listAllForAdmin`, `updateStatus`, `addNote` |
| `internal/repository/SupportTicketRepository.java` | Modify — add cross-tenant filtered query |
| `internal/controller/AdminSupportTicketController.java` | Create (includes admin detail endpoint `GET /api/v1/admin/support-tickets/{id}`) |

## Success Criteria
- `GET /api/v1/admin/support-tickets` returns tickets from all tenants; `?tenantId=<uuid>` narrows results to that tenant.
- `PATCH /api/v1/admin/support-tickets/{id}` with `{"status":"IN_PROGRESS"}` on an OPEN ticket returns 200 and the ticket's status is now IN_PROGRESS.
- `PATCH /api/v1/admin/support-tickets/{id}` with `{"status":"OPEN"}` on a RESOLVED ticket returns 400 (`INVALID_STATUS_TRANSITION`).
- `POST /api/v1/admin/support-tickets/{id}/notes` creates a note; a subsequent `GET /api/v1/support-tickets/{id}` by the host shows the note in the `notes` array with the correct `createdAt`.
- Multiple `POST .../notes` calls create separate note records and all appear in the host's detail view ordered chronologically.
- A HOST_ADMIN calling `GET /api/v1/admin/support-tickets` receives 403 (role guard at class level).

## Acceptance Criteria Mapping
- P1: PLATFORM_ADMIN lists all tickets across tenants, filters by status/category → FR-04.
- P1: PLATFORM_ADMIN updates ticket status OPEN → IN_PROGRESS → RESOLVED → FR-05.
- P2: Host sees all admin notes in chronological order after admin adds them → FR-06.

## Risks
- Cross-tenant list with three optional filters: use Spring Data JPA `Specification` pattern (already present in other modules) to avoid combinatorial JPQL — confirm the pattern is available in the shared layer before writing raw JPQL.
- Audit log entries for `addNote` must record `adminPublicId` from the caller, not a static string — ensure `caller.userPublicId()` is the correct accessor on `CurrentUser` (verify against the existing `CurrentUser` record definition).

### Tests to Write

**`app/src/test/java/com/pte/support/internal/service/SupportTicketServiceTest.java`** (extend from Phase 2):

| Test name | What it verifies |
|---|---|
| `updateStatus_openToInProgress_transitionsAndAudits` | Status changes, audit log recorded |
| `updateStatus_inProgressToResolved_transitionsAndAudits` | Full chain transition |
| `updateStatus_resolvedToOpen_throwsInvalidTransition` | Backward transition rejected with 400 |
| `updateStatus_openToResolved_throwsInvalidTransition` | Skip-a-step transition rejected |
| `updateStatus_unknownTicketPublicId_throwsNotFound` | Admin gets 404 for non-existent ticket |
| `addNote_savesNoteWithAdminPublicId` | Note saved with correct `adminPublicId`, `ticketPublicId`, `content` |
| `addNote_multipleNotes_allPersisted` | Two consecutive calls → two separate note records |
| `listAllForAdmin_noFilters_returnsAllTenants` | Cross-tenant result set |
| `listAllForAdmin_filterByTenantId_narrowsResults` | Only that tenant's tickets returned |
| `listAllForAdmin_filterByStatus_narrowsResults` | Only matching-status tickets returned |

**`app/src/test/java/com/pte/support/internal/repository/SupportTicketRepositoryTest.java`** (`@DataJpaTest`):

| Test name | What it verifies |
|---|---|
| `findByTenantId_excludesOtherTenants` | DB-level tenant isolation on the host list query |
| `findAll_withStatusFilter_returnsOnlyMatchingStatus` | Pagination + status filter at persistence layer |
| `findAll_withTenantIdFilter_returnsOnlyMatchingTenant` | Admin cross-tenant filter at persistence layer |

## Execution Log

### Errors Encountered
- None (all admin methods were implemented in Phase 2; Phase 3 added tests only)

### Root Cause
- N/A

### Resolution
- N/A

### Test Results After Fix
- Post code-review fixes: added guard in `updateStatus` for invalid target status, added `@Size(max=36)` to `entityId`, added `ticket_public_id` index to V71
- `.\mvnw test -pl app -Dtest="SupportTicketServiceTest,SupportTicketRepositoryTest,SupportTicketMapperTest,SupportTicketTest"` → 26 tests, 0 failures — BUILD SUCCESS
