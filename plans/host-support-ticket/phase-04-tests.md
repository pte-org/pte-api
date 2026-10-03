# Phase 4: Tests

## Requirements
All test classes identified in Phases 1–3 are written and pass. Coverage spans entity transitions, service logic, tenant isolation, mapper correctness, and repository query behavior.

## Steps
1. Write `SupportTicketTest` (entity-level, no Spring): covers all status-transition happy paths and all invalid-transition error paths identified in Phase 1.

2. Write `SupportTicketServiceTest` (Mockito, no Spring): covers submit happy paths, entity-reference validation errors, host list/get-detail scoping, admin status transitions, note appending, and cross-tenant list filtering as identified in Phases 2 and 3.

3. Write `SupportTicketTenantIsolationTest` (Mockito, no Spring): dedicated isolation harness ensuring the host list and host get-detail methods never serve data from a different tenant, regardless of the publicId passed.

4. Write `SupportTicketMapperTest` (pure unit): asserts all response fields are populated correctly for both the full detail response (with notes) and the summary response (without notes).

5. Write `SupportTicketRepositoryTest` (`@DataJpaTest`): exercises the host-scoped paginated query and the admin cross-tenant query with all optional filter combinations against an in-memory H2 or Testcontainers Postgres slice. Confirms the `CHECK` constraint on the entity-pair columns rejects a row with exactly one null.

6. Verify overall test suite passes with `mvn test -pl app` and that no existing tests are broken by the new module.

## Files to Create

All paths relative to `d:\FPT\9thSemester\pte\pte-api\app\src\test\java\com\pte\support\`:

| File | What it covers |
|---|---|
| `domain/SupportTicketTest.java` | Entity status-transition methods |
| `internal/service/SupportTicketServiceTest.java` | Submit, list, detail, updateStatus, addNote, admin list |
| `internal/service/SupportTicketTenantIsolationTest.java` | Host cannot see other-tenant data |
| `internal/mapper/SupportTicketMapperTest.java` | Full-detail and summary mapping correctness |
| `internal/repository/SupportTicketRepositoryTest.java` | Host + admin paginated queries, DB-level constraint check |

## Success Criteria
- `mvn test -pl app -Dtest="SupportTicketTest,SupportTicketServiceTest,SupportTicketTenantIsolationTest,SupportTicketMapperTest,SupportTicketRepositoryTest"` exits 0 with all tests green.
- `SupportTicketTenantIsolationTest` contains at least two tests explicitly asserting cross-tenant access returns `SupportTicketNotFoundException`.
- `SupportTicketRepositoryTest` includes at least one test that inserts a row with `entity_type` set and `entity_id` null and expects a constraint violation.
- No test class imports from `com.pte.support.internal.*` other than the layer it is testing (no service tests importing controller classes, etc.).

## Acceptance Criteria Mapping
- Spec success criterion: HOST_ADMIN cannot see tickets from another tenant → `SupportTicketTenantIsolationTest`.
- Spec success criterion: Submit with `entityType=QUESTION` + null `entityId` → 400 → `SupportTicketServiceTest.submit_entityIdNullEntityTypeSet_throwsInvalidPair`.
- Spec success criterion: Submit with non-existent `entityId` → 404 → `SupportTicketServiceTest.submit_entityDoesNotExist_throwsEntityReferenceNotFound`.
- Spec success criterion: Status transitions OPEN→IN_PROGRESS→RESOLVED → `SupportTicketServiceTest.updateStatus_*` tests.
- Spec success criterion: Multiple admin notes readable in order → `SupportTicketServiceTest.addNote_multipleNotes_allPersisted` + mapper test.

## Risks
- `@DataJpaTest` slice may not pick up the `SupportTicket` and `SupportTicketNote` entities if the module is not component-scanned — confirm the test configuration includes the correct entity scan path (`com.pte.support`).
- H2 may not enforce the `CHECK (entity_type IS NULL = entity_id IS NULL)` constraint; use Testcontainers Postgres slice for that specific constraint test if H2 skips it.

### Tests to Write
All test cases for this phase are enumerated in the tables within Phases 1, 2, and 3. This phase compiles and executes them all.

## Execution Log
<!-- Filled by /ck:cook at the end of this phase — leave placeholders when planning -->

### Errors Encountered
- (not executed yet)

### Root Cause
- (not executed yet)

### Resolution
- (not executed yet)

### Test Results After Fix
- (not executed yet)
