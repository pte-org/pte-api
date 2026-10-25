package com.pte.practice.internal.service;

import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.attempt.ResponseConfidence;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.domain.PracticeSessionOperation;
import com.pte.practice.internal.domain.enums.PracticeSessionItemStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionOperationType;
import com.pte.practice.internal.dto.request.PracticeAnswerRequest;
import com.pte.practice.internal.dto.request.PracticeSaveAndExitRequest;
import com.pte.practice.internal.dto.request.PracticeCapabilityManifest;
import com.pte.practice.internal.dto.request.PracticeSessionActionRequest;
import com.pte.practice.internal.dto.request.PracticeSessionStartRequest;
import com.pte.practice.internal.dto.response.PracticeSessionResponse;
import com.pte.practice.internal.exception.PracticeIdempotencyException;
import com.pte.practice.internal.exception.PracticeNotEntitledException;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.exception.PracticeSessionNotFoundException;
import com.pte.practice.internal.mapper.PracticeSessionResponseMapper;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.practice.internal.repository.PracticeSessionItemRepository;
import com.pte.practice.internal.repository.PracticeSessionOperationRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeSessionServiceTest {

    @Mock
    private PracticeSessionRepository sessionRepository;
    @Mock
    private PracticeSessionPersistenceService sessionPersistenceService;
    @Mock
    private PracticeSessionItemRepository itemRepository;
    @Mock
    private PracticeSessionOperationRepository operationRepository;
    @Mock
    private QuestionTypeService questionTypeService;
    @Mock
    private PracticeEntitlementService entitlementService;
    @Mock
    private PracticeAnswerValidationService answerValidationService;

    private PracticeCatalogService catalogService;
    private PracticeSessionService service;
    private CurrentUser caller;
    private UUID tenantId;
    private UUID sessionId;
    private Instant now;

    @BeforeEach
    void setUp() {
        UUID studentId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        now = Instant.parse("2026-10-07T12:00:00Z");
        caller = new CurrentUser(studentId, tenantId, List.of("STUDENT"));
        lenient().when(questionTypeService.list(true)).thenReturn(List.of(
                new com.pte.itembank.dto.response.QuestionTypeResponse(
                        UUID.randomUUID(), "MC_READING_SINGLE", "Multiple-choice Reading (Single)", "MCS",
                        "READING", true, true, 1, false, false, true, true, false, false, true, false,
                        TaskRuntimeProfileRegistry.descriptorFor("MC_READING_SINGLE"), "MC_READING_SINGLE",
                        "MC_READING_SINGLE", "MC_READING_SINGLE_V1", 1, null, null)));
        catalogService = new PracticeCatalogService(questionTypeService);
        PracticeSessionItemService itemService = new PracticeSessionItemService(itemRepository, catalogService,
                answerValidationService);
        PracticeSessionResponseMapper responseMapper = new PracticeSessionResponseMapper(catalogService, itemService);
        service = new PracticeSessionService(sessionRepository, sessionPersistenceService, operationRepository,
                catalogService, entitlementService, itemService, responseMapper,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void startCreatesOverviewAndSameKeyReplaysTheSameSession() {
        java.util.concurrent.atomic.AtomicReference<PracticeSession> persisted =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(sessionRepository.findByStudentPublicIdAndStartIdempotencyKeyAndDeletedFalse(
                caller.userId(), "start-1")).thenAnswer(invocation ->
                        Optional.ofNullable(persisted.get()));
        when(sessionPersistenceService.saveStart(any(PracticeSession.class))).thenAnswer(invocation -> {
            PracticeSession session = invocation.getArgument(0);
            session.setPublicId(sessionId);
            session.setVersion(0L);
            persisted.set(session);
            return session;
        });

        PracticeSessionStartRequest request = startRequest();
        PracticeSessionResponse first = service.start(request, "start-1", caller);
        PracticeSessionResponse replay = service.start(request, "start-1", caller);

        assertThat(first.publicId()).isEqualTo(sessionId);
        assertThat(first.status()).isEqualTo(PracticeSessionStatus.OVERVIEW);
        assertThat(replay.publicId()).isEqualTo(sessionId);
        assertThat(replay.nextAction()).isEqualTo("NEXT");
    }

    @Test
    void skillStartCoversTheRunnableTaskTypesOfThatSkill() {
        when(sessionRepository.findByStudentPublicIdAndStartIdempotencyKeyAndDeletedFalse(
                caller.userId(), "skill-1")).thenReturn(Optional.empty());
        when(sessionPersistenceService.saveStart(any(PracticeSession.class))).thenAnswer(invocation -> {
            PracticeSession session = invocation.getArgument(0);
            session.setPublicId(sessionId);
            session.setVersion(0L);
            return session;
        });

        useQuestionBankWith(Set.of("MC_READING_SINGLE"));

        service.start(skillRequest("READING", Set.of()), "skill-1", caller);

        org.mockito.ArgumentCaptor<PracticeSession> saved = org.mockito.ArgumentCaptor.forClass(PracticeSession.class);
        org.mockito.Mockito.verify(sessionPersistenceService).saveStart(saved.capture());
        assertThat(saved.getValue().effectiveKind()).isEqualTo(
                com.pte.practice.internal.domain.enums.PracticeSessionKind.SKILL);
        assertThat(saved.getValue().getSkillCode()).isEqualTo("READING");
        assertThat(saved.getValue().getSelectedTaskTypes()).isEqualTo("MC_READING_SINGLE");
    }

    @Test
    void skillCannotBeCombinedWithTaskTypesOrMockTest() {
        assertThatThrownBy(() -> service.start(skillRequest("READING", Set.of("MC_READING_SINGLE")), "skill-2", caller))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_SESSION_REQUEST_INVALID");
    }

    @Test
    void skillWithNoRunnableTaskTypeIsNotReady() {
        useQuestionBankWith(Set.of("MC_READING_SINGLE"));
        assertThatThrownBy(() -> service.start(skillRequest("SPEAKING", Set.of()), "skill-3", caller))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_CONTENT_NOT_READY");
    }

    @Test
    void startingAMockTestAgainResumesTheUnfinishedAttempt() {
        PracticeMockTestService mockTests = org.mockito.Mockito.mock(PracticeMockTestService.class);
        service.setMockTestService(mockTests);
        var mockTest = mockTest(7L);
        when(mockTests.requirePublished(mockTest.getPublicId())).thenReturn(mockTest);
        PracticeSession unfinished = savedSession();
        unfinished.setStatus(PracticeSessionStatus.IN_PROGRESS);
        when(sessionRepository.findByStudentPublicIdAndStartIdempotencyKeyAndDeletedFalse(caller.userId(), "mock-1"))
                .thenReturn(Optional.empty());
        when(sessionRepository.findFirstByStudentPublicIdAndMockTestIdAndStatusInAndDeletedFalseOrderByCreatedAtDesc(
                any(), org.mockito.ArgumentMatchers.eq(7L), any())).thenReturn(Optional.of(unfinished));

        PracticeSessionResponse response = service.start(mockTestRequest(mockTest, false), "mock-1", caller);

        assertThat(response.publicId()).isEqualTo(sessionId);
        org.mockito.Mockito.verify(sessionPersistenceService, org.mockito.Mockito.never()).saveStart(any());
    }

    @Test
    void restartingAMockTestDiscardsTheUnfinishedAttemptAndBuildsATimedSession() {
        PracticeMockTestService mockTests = org.mockito.Mockito.mock(PracticeMockTestService.class);
        service.setMockTestService(mockTests);
        var mockTest = mockTest(7L);
        when(mockTests.requirePublished(mockTest.getPublicId())).thenReturn(mockTest);
        when(mockTests.itemsOf(7L)).thenReturn(List.of());
        PracticeSession unfinished = savedSession();
        unfinished.setStatus(PracticeSessionStatus.IN_PROGRESS);
        when(sessionRepository.findByStudentPublicIdAndStartIdempotencyKeyAndDeletedFalse(caller.userId(), "mock-2"))
                .thenReturn(Optional.empty());
        when(sessionRepository.findFirstByStudentPublicIdAndMockTestIdAndStatusInAndDeletedFalseOrderByCreatedAtDesc(
                any(), org.mockito.ArgumentMatchers.eq(7L), any())).thenReturn(Optional.of(unfinished));
        when(sessionPersistenceService.replaceStart(any(), any(), any(PracticeSession.class))).thenAnswer(invocation -> {
            PracticeSession session = invocation.getArgument(2);
            session.setPublicId(UUID.randomUUID());
            session.setVersion(0L);
            return session;
        });

        service.start(mockTestRequest(mockTest, true), "mock-2", caller);

        // The discard and the insert share one transaction (replaceStart); doing the discard here deadlocked on the
        // one-active-attempt unique index.
        assertThat(unfinished.getStatus()).isEqualTo(PracticeSessionStatus.IN_PROGRESS);
        org.mockito.ArgumentCaptor<PracticeSession> saved = org.mockito.ArgumentCaptor.forClass(PracticeSession.class);
        org.mockito.Mockito.verify(sessionPersistenceService).replaceStart(
                org.mockito.ArgumentMatchers.eq(unfinished.getId()), any(), saved.capture());
        org.mockito.Mockito.verify(sessionPersistenceService, org.mockito.Mockito.never()).saveStart(any());
        assertThat(saved.getValue().effectiveKind()).isEqualTo(
                com.pte.practice.internal.domain.enums.PracticeSessionKind.MOCK_TEST);
        assertThat(saved.getValue().effectiveTimingMode()).isEqualTo(
                com.pte.practice.internal.domain.enums.PracticeTimingMode.TIMED);
        assertThat(saved.getValue().getQuestionCount()).isEqualTo(65);
        assertThat(saved.getValue().getTitle()).isEqualTo("PTE Academic Full Mock Test 7");
    }

    @Test
    void aMockTestCannotBeCombinedWithTaskTypes() {
        PracticeMockTestService mockTests = org.mockito.Mockito.mock(PracticeMockTestService.class);
        service.setMockTestService(mockTests);
        var request = new PracticeSessionStartRequest("PTE_CORE_PRACTICE", tenantId, Set.of("MC_READING_SINGLE"),
                new PracticeCapabilityManifest(Set.of("OPTION_SELECTION"), "1.0.0"), null, null, null,
                UUID.randomUUID(), null);

        assertThatThrownBy(() -> service.start(request, "mock-3", caller))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_SESSION_REQUEST_INVALID");
    }

    @Test
    void sameStartKeyWithDifferentRequestIsRejected() {
        PracticeSession existing = savedSession();
        existing.setStartRequestHash("different");
        when(sessionRepository.findByStudentPublicIdAndStartIdempotencyKeyAndDeletedFalse(
                caller.userId(), "start-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.start(startRequest(), "start-1", caller))
                .isInstanceOf(PracticeIdempotencyException.class)
                .hasFieldOrPropertyWithValue("code", "PRACTICE_IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void beginRequiresTheCurrentOptimisticVersionAndLeavesNoSessionDeadline() {
        PracticeSession session = savedSession();
        session.setVersion(2L);
        session.setSelectedTaskTypes("MC_READING_SINGLE");
        session.setClientCapabilities("OPTION_SELECTION");
        when(sessionRepository.findWithLockByPublicIdAndStudentPublicId(sessionId, caller.userId()))
                .thenReturn(Optional.of(session));
        when(sessionRepository.saveAndFlush(any(PracticeSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.begin(sessionId, new PracticeSessionActionRequest(1L), "begin-1", caller))
                .isInstanceOf(PracticeSessionException.class)
                .hasFieldOrPropertyWithValue("code", "PRACTICE_STALE_SESSION_VERSION");

        PracticeSessionResponse response = service.begin(sessionId,
                new PracticeSessionActionRequest(2L), "begin-1", caller);

        assertThat(response.status()).isEqualTo(PracticeSessionStatus.IN_PROGRESS);
        assertThat(response.startedAt()).isEqualTo(now);
        // V133: no session-wide limit; a null deadline never expires.
        assertThat(response.deadlineAt()).isNull();
    }

    @Test
    void beginSameMutationKeyReplaysAndRejectsAConflictingPayload() {
        PracticeSession session = savedSession();
        session.setId(42L);
        session.setVersion(0L);
        session.setSelectedTaskTypes("MC_READING_SINGLE");
        session.setClientCapabilities("OPTION_SELECTION");
        when(sessionRepository.findWithLockByPublicIdAndStudentPublicId(sessionId, caller.userId()))
                .thenReturn(Optional.of(session));
        when(sessionRepository.saveAndFlush(any(PracticeSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        java.util.concurrent.atomic.AtomicReference<PracticeSessionOperation> persisted =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(operationRepository.findByPracticeSessionIdAndOperationTypeAndIdempotencyKeyAndDeletedFalse(
                42L, PracticeSessionOperationType.BEGIN, "begin-1"))
                .thenAnswer(invocation -> Optional.ofNullable(persisted.get()));
        when(operationRepository.save(any(PracticeSessionOperation.class))).thenAnswer(invocation -> {
            PracticeSessionOperation operation = invocation.getArgument(0);
            persisted.set(operation);
            return operation;
        });

        PracticeSessionResponse first = service.begin(sessionId,
                new PracticeSessionActionRequest(0L), "begin-1", caller);
        PracticeSessionResponse replay = service.begin(sessionId,
                new PracticeSessionActionRequest(0L), "begin-1", caller);

        assertThat(first.status()).isEqualTo(PracticeSessionStatus.IN_PROGRESS);
        assertThat(replay.status()).isEqualTo(PracticeSessionStatus.IN_PROGRESS);
        assertThatThrownBy(() -> service.begin(sessionId,
                new PracticeSessionActionRequest(1L), "begin-1", caller))
                .isInstanceOf(PracticeIdempotencyException.class)
                .hasFieldOrPropertyWithValue("code", "PRACTICE_IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void beginRechecksLiveEntitlementBeforeMutation() {
        PracticeSession session = savedSession();
        session.setVersion(0L);
        when(sessionRepository.findWithLockByPublicIdAndStudentPublicId(sessionId, caller.userId()))
                .thenReturn(Optional.of(session));
        doThrow(new PracticeNotEntitledException()).when(entitlementService)
                .assertUnlocked(caller, tenantId);

        assertThatThrownBy(() -> service.begin(sessionId,
                new PracticeSessionActionRequest(0L), "begin-1", caller))
                .isInstanceOf(PracticeNotEntitledException.class);
        assertThat(session.getStatus()).isEqualTo(PracticeSessionStatus.OVERVIEW);
    }

    @Test
    void heartbeatExpiresTheSessionAtTheServerDeadline() {
        PracticeSession session = savedSession();
        session.setStatus(PracticeSessionStatus.IN_PROGRESS);
        session.setVersion(0L);
        session.setDeadlineAt(now.minusSeconds(1));
        when(sessionRepository.findWithLockByPublicIdAndStudentPublicId(sessionId, caller.userId()))
                .thenReturn(Optional.of(session));
        when(sessionRepository.saveAndFlush(any(PracticeSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.heartbeat(sessionId,
                new PracticeSessionActionRequest(0L), "heartbeat-1", caller))
                .isInstanceOf(PracticeSessionException.class)
                .hasFieldOrPropertyWithValue("code", "PRACTICE_SESSION_EXPIRED");
        assertThat(session.getStatus()).isEqualTo(PracticeSessionStatus.EXPIRED);
    }

    @Test
    void anotherStudentCannotReadTheSession() {
        when(sessionRepository.findByPublicIdAndStudentPublicIdAndDeletedFalse(sessionId, caller.userId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(sessionId, caller))
                .isInstanceOf(PracticeSessionNotFoundException.class);
    }

    @Test
    void answerPersistsStudentPayloadAndCompletesTheLastItem() {
        PracticeSession session = savedSession();
        session.setId(42L);
        session.setStatus(PracticeSessionStatus.IN_PROGRESS);
        session.setVersion(0L);
        PracticeSessionItem item = pendingItem(session);
        when(sessionRepository.findWithLockByPublicIdAndStudentPublicId(sessionId, caller.userId()))
                .thenReturn(Optional.of(session));
        when(itemRepository.findWithLockByPublicIdAndPracticeSessionId(item.getPublicId(), 42L))
                .thenReturn(Optional.of(item));
        when(itemRepository.findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(42L))
                .thenReturn(List.of(item));
        when(sessionRepository.saveAndFlush(any(PracticeSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PracticeSessionResponse response = service.answer(sessionId, item.getPublicId(),
                new PracticeAnswerRequest(0L, "{\"selectedOption\":\"a\"}", ResponseConfidence.HIGH),
                "answer-1", caller);

        assertThat(item.getStatus()).isEqualTo(PracticeSessionItemStatus.ANSWERED);
        assertThat(item.getSavedPayload()).isEqualTo("{\"selectedOption\":\"a\"}");
        assertThat(response.status()).isEqualTo(PracticeSessionStatus.COMPLETED);
        assertThat(response.answeredItemCount()).isEqualTo(1);
    }

    @Test
    void skipDoesNotRequireConfidenceAndMarksTheItemSkipped() {
        PracticeSession session = savedSession();
        session.setId(42L);
        session.setStatus(PracticeSessionStatus.IN_PROGRESS);
        session.setVersion(0L);
        PracticeSessionItem item = pendingItem(session);
        when(sessionRepository.findWithLockByPublicIdAndStudentPublicId(sessionId, caller.userId()))
                .thenReturn(Optional.of(session));
        when(itemRepository.findWithLockByPublicIdAndPracticeSessionId(item.getPublicId(), 42L))
                .thenReturn(Optional.of(item));
        when(itemRepository.findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(42L))
                .thenReturn(List.of(item));
        when(sessionRepository.saveAndFlush(any(PracticeSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PracticeSessionResponse response = service.skip(sessionId, item.getPublicId(),
                new PracticeSessionActionRequest(0L), "skip-1", caller);

        assertThat(item.getStatus()).isEqualTo(PracticeSessionItemStatus.SKIPPED);
        assertThat(response.status()).isEqualTo(PracticeSessionStatus.COMPLETED);
        assertThat(response.answeredItemCount()).isZero();
    }

    @Test
    void pausingAStartedSessionKeepsItResumableEvenWithoutAnswers() {
        PracticeSession session = savedSession();
        session.setId(42L);
        session.setStatus(PracticeSessionStatus.IN_PROGRESS);
        session.setVersion(0L);
        PracticeSessionItem item = pendingItem(session);
        when(sessionRepository.findWithLockByPublicIdAndStudentPublicId(sessionId, caller.userId()))
                .thenReturn(Optional.of(session));
        when(itemRepository.findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(42L))
                .thenReturn(List.of(item));
        when(sessionRepository.saveAndFlush(any(PracticeSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PracticeSessionResponse response = service.saveAndExit(sessionId,
                new PracticeSaveAndExitRequest(0L, null, null, null), "exit-1", caller);

        assertThat(response.status()).isEqualTo(PracticeSessionStatus.IN_PROGRESS);
        assertThat(response.discardedAt()).isNull();
    }

    @Test
    void saveAndExitDiscardsASessionThatNeverBegan() {
        PracticeSession session = savedSession();
        session.setId(43L);
        session.setStatus(PracticeSessionStatus.OVERVIEW);
        session.setVersion(0L);
        when(sessionRepository.findWithLockByPublicIdAndStudentPublicId(sessionId, caller.userId()))
                .thenReturn(Optional.of(session));
        when(itemRepository.findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(43L)).thenReturn(List.of());
        when(sessionRepository.saveAndFlush(any(PracticeSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PracticeSessionResponse response = service.saveAndExit(sessionId,
                new PracticeSaveAndExitRequest(0L, null, null, null), "exit-2", caller);

        assertThat(response.status()).isEqualTo(PracticeSessionStatus.DISCARDED);
    }

    @Test
    void saveAndExitPersistsTheCurrentDraftWithoutAnsweringTheItem() {
        PracticeSession session = savedSession();
        session.setId(42L);
        session.setStatus(PracticeSessionStatus.IN_PROGRESS);
        session.setVersion(0L);
        PracticeSessionItem item = pendingItem(session);
        when(sessionRepository.findWithLockByPublicIdAndStudentPublicId(sessionId, caller.userId()))
                .thenReturn(Optional.of(session));
        when(itemRepository.findWithLockByPublicIdAndPracticeSessionId(item.getPublicId(), 42L))
                .thenReturn(Optional.of(item));
        when(itemRepository.findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(42L))
                .thenReturn(List.of(item));
        when(sessionRepository.saveAndFlush(any(PracticeSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PracticeSessionResponse response = service.saveAndExit(sessionId,
                new PracticeSaveAndExitRequest(0L, item.getPublicId(), "{\"selectedOption\":\"a\"}",
                        ResponseConfidence.HIGH), "exit-draft-1", caller);

        assertThat(response.status()).isEqualTo(PracticeSessionStatus.IN_PROGRESS);
        assertThat(item.getStatus()).isEqualTo(PracticeSessionItemStatus.PENDING);
        assertThat(item.getSavedPayload()).isEqualTo("{\"selectedOption\":\"a\"}");
        assertThat(item.getConfidence()).isEqualTo(ResponseConfidence.HIGH);
    }

    /** Rebuilds the service so only these task types have published questions (others are not runnable). */
    private void useQuestionBankWith(Set<String> taskTypesWithQuestions) {
        com.pte.itembank.PracticeQuestionService questionService = org.mockito.Mockito.mock(
                com.pte.itembank.PracticeQuestionService.class);
        lenient().when(questionService.hasPublishedQuestion(any())).thenAnswer(invocation ->
                taskTypesWithQuestions.contains(invocation.<String>getArgument(0)));
        catalogService = new PracticeCatalogService(questionTypeService, questionService);
        PracticeSessionItemService itemService = new PracticeSessionItemService(itemRepository, catalogService,
                answerValidationService);
        service = new PracticeSessionService(sessionRepository, sessionPersistenceService, operationRepository,
                catalogService, entitlementService, itemService,
                new PracticeSessionResponseMapper(catalogService, itemService), Clock.fixed(now, ZoneOffset.UTC));
    }

    private com.pte.practice.internal.domain.PracticeMockTest mockTest(Long id) {
        var test = new com.pte.practice.internal.domain.PracticeMockTest();
        test.setId(id);
        test.setPublicId(UUID.randomUUID());
        test.setSequenceNo(7);
        test.setTitle("PTE Academic Full Mock Test 7");
        test.setItemCount(65);
        return test;
    }

    private PracticeSessionStartRequest mockTestRequest(com.pte.practice.internal.domain.PracticeMockTest test,
            boolean restart) {
        return new PracticeSessionStartRequest("PTE_CORE_PRACTICE", tenantId, Set.of(),
                new PracticeCapabilityManifest(Set.of("OPTION_SELECTION"), "1.0.0"), com.pte.practice.internal.domain.enums.PracticeTimingMode.TIMED, null, null,
                test.getPublicId(), restart);
    }

    private PracticeSessionStartRequest skillRequest(String skill, Set<String> taskTypes) {
        return new PracticeSessionStartRequest("PTE_CORE_PRACTICE", tenantId, taskTypes,
                new PracticeCapabilityManifest(Set.of("OPTION_SELECTION"), "1.0.0"),
                null, 10, skill, null, null);
    }

    private PracticeSessionStartRequest startRequest() {
        return new PracticeSessionStartRequest("PTE_CORE_PRACTICE", tenantId,
                Set.of("MC_READING_SINGLE"), new PracticeCapabilityManifest(Set.of("OPTION_SELECTION"), "1.0.0"));
    }

    private PracticeSession savedSession() {
        PracticeSession session = new PracticeSession();
        session.setPublicId(sessionId);
        session.setStudentPublicId(caller.userId());
        session.setTenantId(tenantId);
        session.setProductCode("PTE_CORE_PRACTICE");
        session.setTitle("PTE Core Practice");
        session.setTimeLimitSeconds(3_600);
        session.setCatalogVersion("2026.10");
        session.setStartIdempotencyKey("start-1");
        session.setStartRequestHash("unused");
        return session;
    }

    private PracticeSessionItem pendingItem(PracticeSession session) {
        PracticeSessionItem item = new PracticeSessionItem();
        item.setPublicId(UUID.randomUUID());
        item.setPracticeSessionId(session.getId());
        item.setOrderIndex(0);
        item.setTaskCode("MC_READING_SINGLE");
        item.setDisplayName("Multiple-choice Reading (Single)");
        item.setSection("READING");
        item.setRendererKey("MC_READING_SINGLE_V1");
        item.setContractVersion(1);
        item.setAnswerSchemaVersion(1);
        item.setStatus(PracticeSessionItemStatus.PENDING);
        return item;
    }
}
