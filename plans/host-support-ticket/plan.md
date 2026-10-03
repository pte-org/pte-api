# Plan: Host Support Ticket
Status: ✅ Complete

## Session Notes
<!-- Updated by cook automatically — do not edit manually -->

**Last active:** 2026-10-03
**Phase in progress:** None (all phases complete)
**Status:** All 4 phases complete. Full support ticket module implemented with domain, host API, admin API, and inline tests.

### Decisions made this session
- `SupportTicketNote` uses a direct FK `ticket_id BIGINT REFERENCES support_ticket(id)` — stays within module boundaries
- `@Version Long version` added to `SupportTicket` for optimistic locking
- `package-info.java` lists `itembank`, `session`, `attempt` as allowed dependencies
- `AttemptService.attemptExistsForTenant` added; `ExamAttemptRepository.existsByPublicIdAndTenantId` added
- Admin methods (`updateStatus`, `addNote`, `listAllForAdmin`, `getDetailForAdmin`) placed directly in `SupportTicketService` — one service for both roles
- `AdminSupportTicketController` created alongside `HostSupportTicketController` (Phase 2 delivered both controllers to keep service complete)

### Next immediate action
Phase 3: Admin API tests + repository slice tests
Date: 2026-10-03
Mode: Hard

## Overview
Adds a `com.pte.support` module that lets HOST_ADMIN submit structured support tickets linked to platform entities, and PLATFORM_ADMIN triage them with status transitions and append-only notes — all with strict tenant isolation.

## Phases
- [x] Phase 1: Domain and Migration — Define JPA entities, enums, and Flyway V71 schema
- [x] Phase 2: Host API — Submit, list, and get-detail endpoints for HOST_ADMIN
- [x] Phase 3: Admin API — List-all, update-status, and add-note endpoints for PLATFORM_ADMIN
- [x] Phase 4: Tests — Unit tests for entities, services, mappers, and repository slices

## Research Summary

**Chosen approach: enrollment module pattern (primary) with entity-method transitions from the alternative.**

Package layout follows enrollment exactly: `domain/`, `domain/enums/`, `internal/controller/`, `internal/service/`, `internal/repository/`, `internal/dto/request/`, `internal/dto/response/`, `internal/exception/`, `internal/mapper/`, `internal/constant/`, and a root `SupportModuleService.java`.

Security: class-level `@PreAuthorize("hasRole('HOST_ADMIN')")` on the host controller; `@PreAuthorize("hasRole('PLATFORM_ADMIN')")` on the admin controller. `CurrentUser` via `CurrentUserContext.required()` inline in controllers.

Multi-tenancy: `tenantId` stored as a plain UUID column on `SupportTicket` (not a FK). The service resolves `caller.tenantId()` from the JWT and scopes every host query to that value; wrong-scope access returns not-found, never 403.

Status transitions are named methods on `SupportTicket` itself (`startProcessing()`, `resolve()`) following the alternative-pattern recommendation. Notes are a separate `SupportTicketNote` table — append-only, no edit/delete.

Cross-module entity references are loose: `entityType` (VARCHAR) + `entityId` (VARCHAR/UUID) with no FK constraint. Existence is validated at submit time via service-layer lookup; stale references after deletion are intentionally tolerated.

Next Flyway migration: **V71**.

## Dependencies
- Existing module services must expose lookup methods for entity-existence validation at submit time:
  - `QuestionModuleService.existsByPublicId(UUID)` or equivalent
  - `ExamSessionModuleService.existsByPublicId(UUID)` or equivalent
  - `AttemptModuleService.existsByPublicId(UUID)` or equivalent
- If any of the above lookup methods do not exist yet they must be added to the respective module facades before Phase 2 can be completed.

## Risks
- HIGH: Cross-module entity validation depends on façade methods that may not exist — mitigate by checking each module's `*ModuleService` before starting Phase 2; add stub lookups if needed.
- MEDIUM: Status transition enforcement only in service layer, not DB constraint — mitigate by explicit guard in `SupportTicket.startProcessing()` / `resolve()` throwing `InvalidStatusTransitionException` before persisting.
- LOW: `entityId` stored as VARCHAR rather than typed UUID column — mitigate with a `@Pattern` annotation on the request DTO and a DB `CHECK` constraint in V71 that validates UUID format. Note: the pair-null CHECK is in Phase 1 Step 4; explicitly add the UUID regex CHECK there too (`CHECK (entity_id IS NULL OR entity_id ~ '^[0-9a-f]{8}-...$')`).
- LOW: Spec NFR states "cross-tenant access → 403" but the plan returns 404 (not-found) to avoid disclosing entity existence. The 404 approach is safer and the spec's success criterion accepts both ("403 hoặc empty list" for list; 404 for detail). Update spec NFR to say 404 if this is queried during code review.
