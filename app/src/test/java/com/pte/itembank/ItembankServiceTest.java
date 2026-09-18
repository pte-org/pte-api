package com.pte.itembank;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionOption;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.domain.enums.Visibility;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.dto.response.QuestionFreezeView;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.exception.InvalidQuestionStatusTransitionException;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.itembank.internal.exception.QuestionValidationException;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.itembank.internal.repository.TaskTypeCountProjection;
import com.pte.itembank.internal.service.ItembankAccessPolicy;
import com.pte.itembank.internal.service.QuestionValidationHelper;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Covers the {@code itembank → assessment} boundary contract (Phase 05/A):
 * {@code create}/{@code get}/{@code listAccessible} preserve the platform-bank
 * rules, and {@code freeze} — the only way {@code assessment} is
 * allowed to read question content — returns options already in delivery
 * order (rotated for {@code RE_ORDER_PARAGRAPHS}, ported from services/authoring's
 * own {@code SnapshotPublishServiceTest.deliveryOrder} coverage).
 */
@ExtendWith(MockitoExtension.class)
class ItembankServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock
    private QuestionRepository questionRepository;

    private ItembankService service;
    private CurrentUser hostCaller;
    private CurrentUser platformCaller;

    @BeforeEach
    void setUp() {
        service = new ItembankService(questionRepository, new QuestionValidationHelper(),
                new ItembankAccessPolicy());
        hostCaller = new CurrentUser(UUID.randomUUID(), TENANT_ID, List.of("HOST_AUTHOR"));
        platformCaller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_AUTHOR"));
    }

    private Question questionWithOptions(PteTaskType taskType, Visibility visibility, UUID tenantId, int optionCount) {
        Question question = new Question();
        question.setPteTaskType(taskType);
        question.setVisibility(visibility);
        question.setTenantId(tenantId);
        question.setStatus(QuestionStatus.APPROVED);
        question.setTitle("test");
        for (int i = 0; i < optionCount; i++) {
            QuestionOption option = new QuestionOption();
            option.setText("option-" + i);
            option.setOrderIndex(i);
            option.setCorrect(false);
            question.addOption(option);
        }
        return question;
    }

    // ------------------------------------------------------------------
    // create — platform-only question-bank write enforcement
    // ------------------------------------------------------------------

    @Test
    void create_sharedByHostCaller_throwsForbidden() {
        CreateQuestionRequest request = new CreateQuestionRequest(
                "READ_ALOUD", "title", "prompt", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(request, hostCaller)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void create_sharedByPlatformCallerPersistsApprovedPlatformQuestion() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CreateQuestionRequest request = new CreateQuestionRequest(
                "READ_ALOUD", "title", "prompt", null, null, null, null, null, null, null);

        QuestionResponse response = service.create(request, platformCaller);

        assertThat(response.visibility()).isEqualTo("SHARED");
        assertThat(response.tenantId()).isNull();
    }

    // ------------------------------------------------------------------
    // get — accessibility gate reused by assessment.BlueprintService
    // ------------------------------------------------------------------

    @Test
    void get_sharedQuestion_readableByAnyTenant() {
        UUID publicId = UUID.randomUUID();
        Question question = questionWithOptions(PteTaskType.READ_ALOUD, Visibility.SHARED, null, 0);
        when(questionRepository.findWithOptionsByPublicId(publicId)).thenReturn(Optional.of(question));

        QuestionResponse response = service.get(publicId, hostCaller);

        assertThat(response.visibility()).isEqualTo("SHARED");
    }

    @Test
    void get_unknownQuestion_throwsNotFound() {
        UUID publicId = UUID.randomUUID();
        when(questionRepository.findWithOptionsByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(publicId, hostCaller)).isInstanceOf(QuestionNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // freeze — the only content-read path assessment is allowed to use
    // ------------------------------------------------------------------

    @Test
    void freeze_unknownQuestion_throwsNotFound() {
        UUID publicId = UUID.randomUUID();
        when(questionRepository.findWithOptionsByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.freeze(publicId)).isInstanceOf(QuestionNotFoundException.class);
    }

    @Test
    void freeze_draftQuestionIsNotAvailableForGeneration() {
        UUID publicId = UUID.randomUUID();
        Question question = questionWithOptions(PteTaskType.READ_ALOUD, Visibility.SHARED, null, 0);
        question.setStatus(QuestionStatus.DRAFT);
        when(questionRepository.findWithOptionsByPublicId(publicId)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.freeze(publicId)).isInstanceOf(QuestionNotFoundException.class);
    }

    @Test
    void freeze_copiesAllScalarFieldsAndOptionMetadata() {
        UUID publicId = UUID.randomUUID();
        Question question = questionWithOptions(PteTaskType.MC_READING_SINGLE, Visibility.SHARED, null, 0);
        question.setPromptText("Passage");
        question.setReferenceAnswerText("ref");
        question.setCorrectAnswerText("correct");
        question.setMinWordCount(10);
        question.setMaxWordCount(20);
        QuestionOption option = new QuestionOption();
        option.setText("A");
        option.setOrderIndex(0);
        option.setCorrect(true);
        option.setBlankIndex(2);
        option.setCorrectGapIndex(1);
        question.addOption(option);
        when(questionRepository.findWithOptionsByPublicId(publicId)).thenReturn(Optional.of(question));

        QuestionFreezeView view = service.freeze(publicId);

        assertThat(view.pteTaskType()).isEqualTo(PteTaskType.MC_READING_SINGLE);
        assertThat(view.promptText()).isEqualTo("Passage");
        assertThat(view.referenceAnswerText()).isEqualTo("ref");
        assertThat(view.correctAnswerText()).isEqualTo("correct");
        assertThat(view.minWordCount()).isEqualTo(10);
        assertThat(view.maxWordCount()).isEqualTo(20);
        assertThat(view.options()).hasSize(1);
        QuestionFreezeView.Option frozenOption = view.options().get(0);
        assertThat(frozenOption.correct()).isTrue();
        assertThat(frozenOption.blankIndex()).isEqualTo(2);
        assertThat(frozenOption.correctGapIndex()).isEqualTo(1);
    }

    @Test
    void freeze_reOrderParagraphs_everyOptionMovesToADifferentIndexThanItsOrderIndex() {
        UUID publicId = UUID.randomUUID();
        Question question = questionWithOptions(PteTaskType.RE_ORDER_PARAGRAPHS, Visibility.SHARED, null, 4);
        when(questionRepository.findWithOptionsByPublicId(publicId)).thenReturn(Optional.of(question));

        QuestionFreezeView view = service.freeze(publicId);

        assertThat(view.options()).hasSize(4);
        for (int arrayIndex = 0; arrayIndex < view.options().size(); arrayIndex++) {
            assertThat(view.options().get(arrayIndex).orderIndex())
                    .as("array index %d must not hold the option whose orderIndex identity equals that index", arrayIndex)
                    .isNotEqualTo(arrayIndex);
        }
        assertThat(view.options().stream().map(QuestionFreezeView.Option::orderIndex))
                .containsExactlyInAnyOrder(0, 1, 2, 3);
    }

    @Test
    void freeze_nonReorderTaskType_naturalOrderPreserved() {
        UUID publicId = UUID.randomUUID();
        Question question = questionWithOptions(PteTaskType.MC_READING_MULTIPLE, Visibility.SHARED, null, 4);
        when(questionRepository.findWithOptionsByPublicId(publicId)).thenReturn(Optional.of(question));

        QuestionFreezeView view = service.freeze(publicId);

        assertThat(view.options().stream().map(QuestionFreezeView.Option::orderIndex))
                .containsExactly(0, 1, 2, 3);
    }

    // ------------------------------------------------------------------
    // publish/archive/unarchive — Plan B Phase 1 (platform-only lifecycle)
    // ------------------------------------------------------------------

    @Test
    void publish_incompleteQuestion_throwsAndKeepsDraft() {
        UUID publicId = UUID.randomUUID();
        // REPEAT_SENTENCE requires an audio prompt; none set here.
        Question question = questionWithOptions(PteTaskType.REPEAT_SENTENCE, Visibility.SHARED, null, 0);
        question.setStatus(QuestionStatus.DRAFT);
        when(questionRepository.findWithOptionsByPublicId(publicId)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.publish(publicId, platformCaller))
                .isInstanceOf(QuestionValidationException.class);
        assertThat(question.getStatus()).isEqualTo(QuestionStatus.DRAFT);
    }

    @Test
    void publish_byPlatformCaller_succeeds() {
        UUID publicId = UUID.randomUUID();
        Question question = questionWithOptions(PteTaskType.READ_ALOUD, Visibility.SHARED, null, 0);
        question.setStatus(QuestionStatus.DRAFT);
        question.setPromptText("read this aloud");
        when(questionRepository.findWithOptionsByPublicId(publicId)).thenReturn(Optional.of(question));

        QuestionResponse response = service.publish(publicId, platformCaller);

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(question.getStatus()).isEqualTo(QuestionStatus.APPROVED);
    }

    @Test
    void publish_byHostCaller_forbidden() {
        UUID publicId = UUID.randomUUID();
        Question question = questionWithOptions(PteTaskType.READ_ALOUD, Visibility.SHARED, null, 0);
        question.setStatus(QuestionStatus.DRAFT);
        question.setAudioPromptRef(UUID.randomUUID());
        when(questionRepository.findWithOptionsByPublicId(publicId)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.publish(publicId, hostCaller))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(question.getStatus()).isEqualTo(QuestionStatus.DRAFT);
    }

    @Test
    void publish_archivedQuestion_rejectedWithInvalidTransition() {
        UUID publicId = UUID.randomUUID();
        Question question = questionWithOptions(PteTaskType.READ_ALOUD, Visibility.SHARED, null, 0);
        question.setAudioPromptRef(UUID.randomUUID());
        question.setStatus(QuestionStatus.ARCHIVED);
        when(questionRepository.findWithOptionsByPublicId(publicId)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.publish(publicId, platformCaller))
                .isInstanceOf(InvalidQuestionStatusTransitionException.class);
        assertThat(question.getStatus()).isEqualTo(QuestionStatus.ARCHIVED);
    }

    @Test
    void publish_alreadyApproved_isIdempotentNoOp() {
        UUID publicId = UUID.randomUUID();
        Question question = questionWithOptions(PteTaskType.READ_ALOUD, Visibility.SHARED, null, 0);
        question.setAudioPromptRef(UUID.randomUUID());
        question.setStatus(QuestionStatus.APPROVED);
        when(questionRepository.findWithOptionsByPublicId(publicId)).thenReturn(Optional.of(question));

        QuestionResponse response = service.publish(publicId, platformCaller);

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(question.getStatus()).isEqualTo(QuestionStatus.APPROVED);
    }

    @Test
    void archive_fromDraftOrApproved_succeeds() {
        UUID draftId = UUID.randomUUID();
        Question draft = questionWithOptions(PteTaskType.READ_ALOUD, Visibility.SHARED, null, 0);
        draft.setStatus(QuestionStatus.DRAFT);
        when(questionRepository.findWithOptionsByPublicId(draftId)).thenReturn(Optional.of(draft));

        service.archive(draftId, platformCaller);

        assertThat(draft.getStatus()).isEqualTo(QuestionStatus.ARCHIVED);

        UUID approvedId = UUID.randomUUID();
        Question approved = questionWithOptions(PteTaskType.READ_ALOUD, Visibility.SHARED, null, 0);
        approved.setStatus(QuestionStatus.APPROVED);
        when(questionRepository.findWithOptionsByPublicId(approvedId)).thenReturn(Optional.of(approved));

        service.archive(approvedId, platformCaller);

        assertThat(approved.getStatus()).isEqualTo(QuestionStatus.ARCHIVED);
    }

    @Test
    void archive_alreadyArchived_isIdempotentNoOp() {
        UUID publicId = UUID.randomUUID();
        Question question = questionWithOptions(PteTaskType.READ_ALOUD, Visibility.SHARED, null, 0);
        question.setStatus(QuestionStatus.ARCHIVED);
        when(questionRepository.findWithOptionsByPublicId(publicId)).thenReturn(Optional.of(question));

        service.archive(publicId, platformCaller);

        assertThat(question.getStatus()).isEqualTo(QuestionStatus.ARCHIVED);
    }

    @Test
    void archive_byHostCaller_forbidden() {
        UUID publicId = UUID.randomUUID();
        Question question = questionWithOptions(PteTaskType.READ_ALOUD, Visibility.SHARED, null, 0);
        question.setStatus(QuestionStatus.DRAFT);
        when(questionRepository.findWithOptionsByPublicId(publicId)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.archive(publicId, hostCaller))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(question.getStatus()).isEqualTo(QuestionStatus.DRAFT);
    }

    @Test
    void unarchive_archivedQuestion_becomesDraft_notApproved() {
        UUID publicId = UUID.randomUUID();
        Question question = questionWithOptions(PteTaskType.READ_ALOUD, Visibility.SHARED, null, 0);
        question.setStatus(QuestionStatus.ARCHIVED);
        when(questionRepository.findWithOptionsByPublicId(publicId)).thenReturn(Optional.of(question));

        service.unarchive(publicId, platformCaller);

        assertThat(question.getStatus()).isEqualTo(QuestionStatus.DRAFT);
    }

    @Test
    void unarchive_draftOrApproved_rejectedWithInvalidTransition() {
        UUID draftId = UUID.randomUUID();
        Question draft = questionWithOptions(PteTaskType.READ_ALOUD, Visibility.SHARED, null, 0);
        draft.setStatus(QuestionStatus.DRAFT);
        when(questionRepository.findWithOptionsByPublicId(draftId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.unarchive(draftId, platformCaller))
                .isInstanceOf(InvalidQuestionStatusTransitionException.class);

        UUID approvedId = UUID.randomUUID();
        Question approved = questionWithOptions(PteTaskType.READ_ALOUD, Visibility.SHARED, null, 0);
        approved.setStatus(QuestionStatus.APPROVED);
        when(questionRepository.findWithOptionsByPublicId(approvedId)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.unarchive(approvedId, platformCaller))
                .isInstanceOf(InvalidQuestionStatusTransitionException.class);
    }

    @Test
    void unarchive_byHostCaller_forbidden() {
        UUID publicId = UUID.randomUUID();
        Question question = questionWithOptions(PteTaskType.READ_ALOUD, Visibility.SHARED, null, 0);
        question.setStatus(QuestionStatus.ARCHIVED);
        when(questionRepository.findWithOptionsByPublicId(publicId)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.unarchive(publicId, hostCaller))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(question.getStatus()).isEqualTo(QuestionStatus.ARCHIVED);
    }

    // ------------------------------------------------------------------
    // countPublishedByTaskTypes / randomPublishedQuestionIds — generation facade
    // ------------------------------------------------------------------

    @Test
    void countPublishedByTaskTypes_excludesDraftArchivedAndPrivate() {
        Set<PteTaskType> taskTypes = Set.of(PteTaskType.READ_ALOUD, PteTaskType.WRITE_ESSAY);
        TaskTypeCountProjection row = projection("READ_ALOUD", 3);
        when(questionRepository.countPublishedSharedGroupedByTaskType(anySet())).thenReturn(List.of(row));

        Map<PteTaskType, Long> counts = service.countPublishedByTaskTypes(taskTypes);

        assertThat(counts).containsEntry(PteTaskType.READ_ALOUD, 3L);
        assertThat(counts).containsEntry(PteTaskType.WRITE_ESSAY, 0L);
    }

    @Test
    void randomPublishedQuestionIds_returnsAtMostNPublishedSharedIds() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(questionRepository.randomPublishedSharedIdsByTaskType(eq("READ_ALOUD"), eq(5)))
                .thenReturn(List.of(id1, id2));

        List<UUID> ids = service.randomPublishedQuestionIds(PteTaskType.READ_ALOUD, 5);

        assertThat(ids).containsExactly(id1, id2);
    }

    @Test
    void generationFacades_neverTakeACurrentUserParameter() throws NoSuchMethodException {
        assertThatThrownBy(() -> ItembankService.class.getMethod(
                "countPublishedByTaskTypes", Set.class, CurrentUser.class))
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> ItembankService.class.getMethod(
                "randomPublishedQuestionIds", PteTaskType.class, int.class, CurrentUser.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    private TaskTypeCountProjection projection(String taskType, long count) {
        return new TaskTypeCountProjection() {
            @Override
            public String getTaskType() {
                return taskType;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }
}
