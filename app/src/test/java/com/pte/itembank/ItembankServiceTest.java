package com.pte.itembank;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionOption;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.domain.enums.Visibility;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.dto.response.QuestionFreezeView;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.config.PteTaskTypeSkillMapping;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.itembank.internal.repository.QuestionRepository;
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
    @Mock
    private PteTaskTypeSkillMapping skillMapping;

    private ItembankService service;
    private CurrentUser hostCaller;
    private CurrentUser platformCaller;

    @BeforeEach
    void setUp() {
        service = new ItembankService(questionRepository, new QuestionValidationHelper(),
                new ItembankAccessPolicy(), skillMapping);
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
        when(skillMapping.skillsFor(any())).thenReturn(Set.of());
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
        when(skillMapping.skillsFor(any())).thenReturn(Set.of());
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

    @Test
    void countSharedByTaskTypesReturnsRepositoryCounts() {
        when(questionRepository.countByVisibilityAndTaskTypeIn(eq(Visibility.SHARED), any()))
                .thenReturn(List.<Object[]>of(new Object[]{PteTaskType.READ_ALOUD, 3L}));

        Map<PteTaskType, Long> counts = service.countSharedByTaskTypes(Set.of(PteTaskType.READ_ALOUD));

        assertThat(counts).containsEntry(PteTaskType.READ_ALOUD, 3L);
    }

    @Test
    void findRandomByTaskTypeMapsRepositoryQuestionsToFreezeViews() {
        Question first = questionWithOptions(PteTaskType.READ_ALOUD, Visibility.SHARED, null, 0);
        Question second = questionWithOptions(PteTaskType.READ_ALOUD, Visibility.SHARED, null, 0);
        first.setTitle("first");
        second.setTitle("second");
        when(questionRepository.findRandomByTaskType("READ_ALOUD", 2, 11L))
                .thenReturn(List.of(first, second));

        List<QuestionFreezeView> result = service.findRandomByTaskType(PteTaskType.READ_ALOUD, 2, 11L);

        assertThat(result).extracting(QuestionFreezeView::title).containsExactly("first", "second");
    }

    @Test
    void freeze_draftQuestionIsNotAvailableForGeneration() {
        UUID publicId = UUID.randomUUID();
        Question question = questionWithOptions(PteTaskType.READ_ALOUD, Visibility.SHARED, null, 0);
        question.setStatus(QuestionStatus.DRAFT);
        when(questionRepository.findWithOptionsByPublicId(publicId)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.freeze(publicId)).isInstanceOf(QuestionNotFoundException.class);
    }
}
