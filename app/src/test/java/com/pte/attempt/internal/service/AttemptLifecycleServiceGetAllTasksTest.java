package com.pte.attempt.internal.service;

import com.pte.attempt.internal.config.EncryptionKeyProvider;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.PinnedExamSnapshot;
import com.pte.attempt.domain.PinnedItem;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.dto.response.AttemptTaskResponse;
import com.pte.attempt.internal.mapper.AttemptMapper;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.attempt.internal.repository.PinnedItemRepository;
import com.pte.attempt.internal.service.cache.PinnedItemView;
import com.pte.attempt.internal.service.cache.PinnedSnapshotCacheService;
import com.pte.shared.security.CurrentUser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Covers {@link AttemptLifecycleService#getAllTasks}: the bulk prefetch
 * endpoint returns all pinned items in order, with correct section flags
 * ({@code canNavigatePrevious}/{@code canNavigateNext}) and correct
 * {@code prepSeconds}/{@code responseSeconds} per item.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttemptLifecycleService.getAllTasks")
class AttemptLifecycleServiceGetAllTasksTest {

    @Mock
    private ExamAttemptRepository attemptRepository;

    @Mock
    private PinnedItemRepository pinnedItemRepository;

    @Mock
    private SnapshotPinService snapshotPinService;

    @Mock
    private PinnedSnapshotCacheService cacheService;

    @Mock
    private TimerService timerService;

    @Mock
    private AnswerSubmitService answerSubmitService;

    @Mock
    private EncryptionKeyProvider encryptionKeyProvider;

    @Mock
    private SubmissionDecryptionService submissionDecryptionService;

    @Mock
    private HeartbeatService heartbeatService;

    private AttemptLifecycleService service;

    private UUID studentPublicId;
    private CurrentUser caller;
    private UUID attemptPublicId;
    private ExamAttempt attempt;
    private PinnedExamSnapshot snapshot;

    @BeforeEach
    void setUp() {
        service = new AttemptLifecycleService(
                attemptRepository, pinnedItemRepository, snapshotPinService, cacheService,
                timerService, answerSubmitService, new AttemptMapper(JsonMapper.builder().build()),
                encryptionKeyProvider, submissionDecryptionService, heartbeatService);

        studentPublicId = UUID.randomUUID();
        caller = new CurrentUser(studentPublicId, UUID.randomUUID(), List.of("student"));
        attemptPublicId = UUID.randomUUID();

        attempt = new ExamAttempt();
        attempt.setId(1L);
        attempt.setStudentPublicId(studentPublicId);
        attempt.setTenantId(caller.tenantId());
        attempt.begin();
        attempt.setCurrentOrderIndex(0);

        snapshot = new PinnedExamSnapshot();
        snapshot.setId(1L);
        snapshot.setAnswerIntegrityLevel("STANDARD");
        attempt.setPinnedSnapshot(snapshot);

        when(attemptRepository.findWithPinnedByPublicIdAndStudentPublicId(attemptPublicId, studentPublicId))
                .thenReturn(Optional.of(attempt));
    }

    private PinnedItem item(int orderIndex, String section, String taskType) {
        PinnedItem item = new PinnedItem();
        item.setId((long) orderIndex + 1);
        item.setPublicId(UUID.randomUUID());
        item.setOrderIndex(orderIndex);
        item.setSection(section);
        item.setTaskType(taskType);
        item.setPrepSeconds(0);
        item.setResponseSeconds(60);
        item.setPinnedSnapshot(snapshot);
        return item;
    }

    @Test
    @DisplayName("returns all items in order for a PRACTICE attempt")
    void getAllTasks_practiceMode_returnsAllItemsInOrder() {
        snapshot.setExamMode("PRACTICE");
        PinnedItem item0 = item(0, "SPEAKING", "READ_ALOUD");
        PinnedItem item1 = item(1, "SPEAKING", "READ_ALOUD");
        PinnedItem item2 = item(2, "SPEAKING", "READ_ALOUD");
        snapshot.addItem(item0);
        snapshot.addItem(item1);
        snapshot.addItem(item2);

        PinnedItemView view0 = PinnedSnapshotCacheService.toView(item0);
        PinnedItemView view1 = PinnedSnapshotCacheService.toView(item1);
        PinnedItemView view2 = PinnedSnapshotCacheService.toView(item2);

        when(timerService.resolveEffectivePrepSeconds(view0)).thenReturn(0);
        when(timerService.resolveEffectiveResponseSeconds(attempt, view0, List.of(view0, view1, view2))).thenReturn(60);
        when(timerService.resolveEffectivePrepSeconds(view1)).thenReturn(0);
        when(timerService.resolveEffectiveResponseSeconds(attempt, view1, List.of(view0, view1, view2))).thenReturn(60);
        when(timerService.resolveEffectivePrepSeconds(view2)).thenReturn(0);
        when(timerService.resolveEffectiveResponseSeconds(attempt, view2, List.of(view0, view1, view2))).thenReturn(60);

        List<AttemptTaskResponse> result = service.getAllTasks(attemptPublicId, caller);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).task().orderIndex()).isEqualTo(0);
        assertThat(result.get(1).task().orderIndex()).isEqualTo(1);
        assertThat(result.get(2).task().orderIndex()).isEqualTo(2);
        assertThat(result.get(0).completed()).isFalse();
    }

    @Test
    @DisplayName("PRACTICE mode: first item has canNavigatePrevious=false, others true")
    void getAllTasks_practiceMode_navigationFlags() {
        snapshot.setExamMode("PRACTICE");
        PinnedItem item0 = item(0, "SPEAKING", "READ_ALOUD");
        PinnedItem item1 = item(1, "SPEAKING", "READ_ALOUD");
        PinnedItem item2 = item(2, "SPEAKING", "READ_ALOUD");
        snapshot.addItem(item0);
        snapshot.addItem(item1);
        snapshot.addItem(item2);

        PinnedItemView view0 = PinnedSnapshotCacheService.toView(item0);
        PinnedItemView view1 = PinnedSnapshotCacheService.toView(item1);
        PinnedItemView view2 = PinnedSnapshotCacheService.toView(item2);
        List<PinnedItemView> allViews = List.of(view0, view1, view2);

        when(timerService.resolveEffectivePrepSeconds(view0)).thenReturn(0);
        when(timerService.resolveEffectiveResponseSeconds(attempt, view0, allViews)).thenReturn(60);
        when(timerService.resolveEffectivePrepSeconds(view1)).thenReturn(0);
        when(timerService.resolveEffectiveResponseSeconds(attempt, view1, allViews)).thenReturn(60);
        when(timerService.resolveEffectivePrepSeconds(view2)).thenReturn(0);
        when(timerService.resolveEffectiveResponseSeconds(attempt, view2, allViews)).thenReturn(60);

        List<AttemptTaskResponse> result = service.getAllTasks(attemptPublicId, caller);

        // first item: no prev, has next
        assertThat(result.get(0).task().canNavigatePrevious()).isFalse();
        assertThat(result.get(0).task().canNavigateNext()).isTrue();
        // middle item: both directions
        assertThat(result.get(1).task().canNavigatePrevious()).isTrue();
        assertThat(result.get(1).task().canNavigateNext()).isTrue();
        // last item: has prev, canNavigateNext=true (pressing Next completes the attempt)
        assertThat(result.get(2).task().canNavigatePrevious()).isTrue();
        assertThat(result.get(2).task().canNavigateNext()).isTrue();
    }

    @Test
    @DisplayName("TEST mode SPEAKING: both nav flags false")
    void getAllTasks_testModeSpeaking_noNavigation() {
        snapshot.setExamMode("TEST");
        PinnedItem item0 = item(0, "SPEAKING", "READ_ALOUD");
        PinnedItem item1 = item(1, "SPEAKING", "READ_ALOUD");
        snapshot.addItem(item0);
        snapshot.addItem(item1);

        PinnedItemView view0 = PinnedSnapshotCacheService.toView(item0);
        PinnedItemView view1 = PinnedSnapshotCacheService.toView(item1);
        List<PinnedItemView> allViews = List.of(view0, view1);

        when(timerService.resolveEffectivePrepSeconds(view0)).thenReturn(0);
        when(timerService.resolveEffectiveResponseSeconds(attempt, view0, allViews)).thenReturn(40);
        when(timerService.resolveEffectivePrepSeconds(view1)).thenReturn(0);
        when(timerService.resolveEffectiveResponseSeconds(attempt, view1, allViews)).thenReturn(40);

        List<AttemptTaskResponse> result = service.getAllTasks(attemptPublicId, caller);

        assertThat(result.get(0).task().canNavigatePrevious()).isFalse();
        assertThat(result.get(0).task().canNavigateNext()).isFalse();
        assertThat(result.get(1).task().canNavigatePrevious()).isFalse();
        assertThat(result.get(1).task().canNavigateNext()).isFalse();
    }

    @Test
    @DisplayName("TEST mode READING: canNavigateNext within section, canNavigatePrevious false")
    void getAllTasks_testModeReading_navigationWithinSection() {
        snapshot.setExamMode("TEST");
        PinnedItem item0 = item(0, "READING", "READING_AND_WRITING_FILL_IN_THE_BLANKS");
        PinnedItem item1 = item(1, "READING", "READING_AND_WRITING_FILL_IN_THE_BLANKS");
        PinnedItem item2 = item(2, "READING", "READING_AND_WRITING_FILL_IN_THE_BLANKS");
        snapshot.addItem(item0);
        snapshot.addItem(item1);
        snapshot.addItem(item2);

        PinnedItemView view0 = PinnedSnapshotCacheService.toView(item0);
        PinnedItemView view1 = PinnedSnapshotCacheService.toView(item1);
        PinnedItemView view2 = PinnedSnapshotCacheService.toView(item2);
        List<PinnedItemView> allViews = List.of(view0, view1, view2);

        when(timerService.resolveEffectivePrepSeconds(view0)).thenReturn(0);
        when(timerService.resolveEffectiveResponseSeconds(attempt, view0, allViews)).thenReturn(600);
        when(timerService.resolveEffectivePrepSeconds(view1)).thenReturn(0);
        when(timerService.resolveEffectiveResponseSeconds(attempt, view1, allViews)).thenReturn(600);
        when(timerService.resolveEffectivePrepSeconds(view2)).thenReturn(0);
        when(timerService.resolveEffectiveResponseSeconds(attempt, view2, allViews)).thenReturn(600);

        List<AttemptTaskResponse> result = service.getAllTasks(attemptPublicId, caller);

        // first reading item: prev=false (index 0), next=true
        assertThat(result.get(0).task().canNavigatePrevious()).isFalse();
        assertThat(result.get(0).task().canNavigateNext()).isTrue();
        // middle: prev=true (adjacent reading item), next=true
        assertThat(result.get(1).task().canNavigatePrevious()).isTrue();
        assertThat(result.get(1).task().canNavigateNext()).isTrue();
        // last: prev=true, canNavigateNext=true (pressing Next completes the attempt)
        assertThat(result.get(2).task().canNavigatePrevious()).isTrue();
        assertThat(result.get(2).task().canNavigateNext()).isTrue();
    }

    @Test
    @DisplayName("completed attempt returns a single completed response, not a task list")
    void getAllTasks_completedAttempt_returnsCompletedResponse() {
        attempt.submit();

        List<AttemptTaskResponse> result = service.getAllTasks(attemptPublicId, caller);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).completed()).isTrue();
        assertThat(result.get(0).task()).isNull();
    }

    @Test
    @DisplayName("encryptionPublicKey is populated for STRICT-pinned attempts")
    void getAllTasks_strictPinned_encryptionKeyPopulated() {
        snapshot.setExamMode("PRACTICE");
        snapshot.setAnswerIntegrityLevel("STRICT");
        PinnedItem item0 = item(0, "SPEAKING", "READ_ALOUD");
        snapshot.addItem(item0);

        PinnedItemView view0 = PinnedSnapshotCacheService.toView(item0);
        when(timerService.resolveEffectivePrepSeconds(view0)).thenReturn(0);
        when(timerService.resolveEffectiveResponseSeconds(attempt, view0, List.of(view0))).thenReturn(60);
        when(encryptionKeyProvider.getPublicKeyBase64()).thenReturn("base64key==");

        List<AttemptTaskResponse> result = service.getAllTasks(attemptPublicId, caller);

        assertThat(result.get(0).encryptionPublicKey()).isEqualTo("base64key==");
    }

    @Test
    @DisplayName("totalTasks reflects the full list size, not just the current position")
    void getAllTasks_totalTasksIsFullListSize() {
        snapshot.setExamMode("PRACTICE");
        for (int i = 0; i < 5; i++) {
            snapshot.addItem(item(i, "SPEAKING", "READ_ALOUD"));
        }
        attempt.setCurrentOrderIndex(2);  // student is mid-exam

        List<PinnedItemView> allViews = snapshot.getItems().stream()
                .map(PinnedSnapshotCacheService::toView).toList();
        for (PinnedItemView v : allViews) {
            when(timerService.resolveEffectivePrepSeconds(v)).thenReturn(0);
            when(timerService.resolveEffectiveResponseSeconds(attempt, v, allViews)).thenReturn(60);
        }

        List<AttemptTaskResponse> result = service.getAllTasks(attemptPublicId, caller);

        assertThat(result).hasSize(5);
        result.forEach(r -> assertThat(r.task().totalTasks()).isEqualTo(5));
    }
}
