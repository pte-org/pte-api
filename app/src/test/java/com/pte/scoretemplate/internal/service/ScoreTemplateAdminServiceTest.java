package com.pte.scoretemplate.internal.service;

import com.pte.scoretemplate.domain.ScoreTemplate;
import com.pte.scoretemplate.domain.ScoreTemplateItem;
import com.pte.scoretemplate.domain.enums.ScoreTemplateStatus;
import com.pte.scoretemplate.domain.enums.ScoringMethod;
import com.pte.scoretemplate.dto.request.CreateScoreTemplateRequest;
import com.pte.scoretemplate.dto.request.ReplaceScoreTemplateItemsRequest;
import com.pte.scoretemplate.dto.request.ScoreTemplateItemRequest;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.scoretemplate.internal.exception.ScoreTemplateConcurrentModificationException;
import com.pte.scoretemplate.internal.exception.ScoreTemplateNotDraftException;
import com.pte.scoretemplate.internal.exception.ScoreTemplateValidationException;
import com.pte.scoretemplate.internal.repository.ScoreTemplateRepository;
import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.domain.enums.PteTaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Mirrors SnapshotPublishServiceTest's style: Mockito, no Spring context. */
@ExtendWith(MockitoExtension.class)
class ScoreTemplateAdminServiceTest {

    @Mock
    private ScoreTemplateRepository repository;

    @Mock
    private QuestionTypeService questionTypeService;

    private ScoreTemplateAdminService service;

    @BeforeEach
    void setUp() {
        service = new ScoreTemplateAdminService(repository);
    }

    /** 22 items covering every required task type + every skill weight, valid per FR-05. */
    private ScoreTemplate fullyValidTemplate(UUID publicId, String code, int version, ScoreTemplateStatus status) {
        ScoreTemplate template = new ScoreTemplate();
        template.setPublicId(publicId);
        template.setCode(code);
        template.setVersion(version);
        template.setName(code + " v" + version);
        template.setStatus(status);
        List<String> taskTypes = List.of(
                "READ_ALOUD", "REPEAT_SENTENCE", "DESCRIBE_IMAGE", "RE_TELL_LECTURE", "ANSWER_SHORT_QUESTION",
                "RESPOND_TO_A_SITUATION", "SUMMARIZE_GROUP_DISCUSSION",
                "SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY",
                "MC_READING_SINGLE", "MC_READING_MULTIPLE", "RE_ORDER_PARAGRAPHS", "FILL_IN_THE_BLANKS_DRAG_AND_DROP",
                "FILL_IN_THE_BLANKS_DROPDOWN",
                "SUMMARIZE_SPOKEN_TEXT", "MC_LISTENING_SINGLE", "MC_LISTENING_MULTIPLE", "FILL_IN_THE_BLANKS_TYPE_IN",
                "HIGHLIGHT_CORRECT_SUMMARY", "SELECT_MISSING_WORD", "HIGHLIGHT_INCORRECT_WORDS", "WRITE_FROM_DICTATION");
        // Every weight goes to the last item (100) and every other item gets
        // 0 — the simplest distribution satisfying both "every skill total >
        // 0" and ScoreTemplateActivationValidator's "every skill total ==
        // exactly 100" check.
        int lastIndex = taskTypes.size() - 1;
        for (int i = 0; i <= lastIndex; i++) {
            BigDecimal weight = i == lastIndex ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
            ScoreTemplateItem item = new ScoreTemplateItem();
            item.setTaskType(taskTypes.get(i));
            item.setSection("SPEAKING");
            item.setSequence(i);
            item.setMinCount(1);
            item.setMaxCount(2);
            item.setPrepSeconds(0);
            item.setResponseSeconds(30);
            item.setScoringMethod(ScoringMethod.AI_SPEECH);
            item.setOverallWeight(weight);
            item.setSpeakingWeight(weight);
            item.setWritingWeight(weight);
            item.setReadingWeight(weight);
            item.setListeningWeight(weight);
            template.addItem(item);
        }
        return template;
    }

    @Test
    void activate_flipsOldActiveToRetired_inSameCall() {
        UUID oldActiveId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ScoreTemplate oldActive = fullyValidTemplate(oldActiveId, "APEUNI_V5", 1, ScoreTemplateStatus.ACTIVE);
        ScoreTemplate target = fullyValidTemplate(targetId, "APEUNI_V5", 2, ScoreTemplateStatus.DRAFT);
        when(repository.findWithItemsByPublicId(targetId)).thenReturn(Optional.of(target));
        when(repository.findAllByCodeForUpdate("APEUNI_V5")).thenReturn(List.of(oldActive, target));
        when(repository.findWithItemsByStatus(ScoreTemplateStatus.ACTIVE)).thenReturn(Optional.of(oldActive));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScoreTemplateResponse response = service.activate(targetId);

        assertThat(oldActive.getStatus()).isEqualTo(ScoreTemplateStatus.RETIRED);
        assertThat(target.getStatus()).isEqualTo(ScoreTemplateStatus.ACTIVE);
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void activate_invalidTemplate_changesNoState() {
        UUID targetId = UUID.randomUUID();
        ScoreTemplate invalid = new ScoreTemplate(); // no items -> fails FR-05
        invalid.setPublicId(targetId);
        invalid.setCode("APEUNI_V5");
        invalid.setVersion(2);
        invalid.setName("broken");
        invalid.setStatus(ScoreTemplateStatus.DRAFT);
        when(repository.findWithItemsByPublicId(targetId)).thenReturn(Optional.of(invalid));

        assertThatThrownBy(() -> service.activate(targetId)).isInstanceOf(ScoreTemplateValidationException.class);

        assertThat(invalid.getStatus()).isEqualTo(ScoreTemplateStatus.DRAFT);
        verify(repository, never()).save(any());
    }

    @Test
    void activate_repositoryRaceViolation_translatesTo409() {
        UUID targetId = UUID.randomUUID();
        ScoreTemplate target = fullyValidTemplate(targetId, "APEUNI_V5", 2, ScoreTemplateStatus.DRAFT);
        when(repository.findWithItemsByPublicId(targetId)).thenReturn(Optional.of(target));
        when(repository.findAllByCodeForUpdate("APEUNI_V5")).thenReturn(List.of(target));
        when(repository.findWithItemsByStatus(ScoreTemplateStatus.ACTIVE)).thenReturn(Optional.empty());
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.activate(targetId))
                .isInstanceOf(ScoreTemplateConcurrentModificationException.class);
    }

    @Test
    void replaceItems_onNonDraftTemplate_throwsNotDraft() {
        UUID publicId = UUID.randomUUID();
        ScoreTemplate active = fullyValidTemplate(publicId, "APEUNI_V5", 1, ScoreTemplateStatus.ACTIVE);
        when(repository.findWithItemsByPublicId(publicId)).thenReturn(Optional.of(active));

        var request = new ReplaceScoreTemplateItemsRequest("new name", List.of(sampleItemRequest()));

        assertThatThrownBy(() -> service.replaceItems(publicId, request)).isInstanceOf(ScoreTemplateNotDraftException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void replaceItems_allowsEmptyDraftWhileItIsBeingBuilt() {
        UUID publicId = UUID.randomUUID();
        ScoreTemplate draft = fullyValidTemplate(publicId, "CUSTOM", 1, ScoreTemplateStatus.DRAFT);
        when(repository.findWithItemsByPublicId(publicId)).thenReturn(Optional.of(draft));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ScoreTemplateResponse response = service.replaceItems(
                publicId, new ReplaceScoreTemplateItemsRequest("Empty draft", List.of()));

        assertThat(response.name()).isEqualTo("Empty draft");
        assertThat(response.items()).isEmpty();
    }

    /**
     * Every skill column on {@code replaceItems} must total exactly 100
     * (validated on every save, not just activate) — so a test asserting on
     * one item's derived fields must pad the other 3 skill totals up to 100
     * with a second item, even though that item is otherwise irrelevant to
     * the assertion.
     */
    private ScoreTemplateItemRequest paddingItemRequest(String taskType, BigDecimal speaking, BigDecimal writing,
                                                         BigDecimal reading, BigDecimal listening) {
        return new ScoreTemplateItemRequest(taskType, "SPEAKING", 1, 1, 1, 0, 30,
                speaking, writing, reading, listening);
    }

    @Test
    void replaceItems_derivesScoringMethodFromTaskType_notFromRequest() {
        UUID publicId = UUID.randomUUID();
        ScoreTemplate draft = fullyValidTemplate(publicId, "CUSTOM", 1, ScoreTemplateStatus.DRAFT);
        when(repository.findWithItemsByPublicId(publicId)).thenReturn(Optional.of(draft));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // sampleItemRequest(): speaking=9, writing/reading/listening=0 — padded to 100 per column.
        var padding = paddingItemRequest("WRITE_ESSAY", BigDecimal.valueOf(91), BigDecimal.valueOf(100),
                BigDecimal.valueOf(100), BigDecimal.valueOf(100));

        ScoreTemplateResponse response = service.replaceItems(
                publicId, new ReplaceScoreTemplateItemsRequest("Custom", List.of(sampleItemRequest(), padding)));

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).scoringMethod()).isEqualTo("AI_SPEECH");
        assertThat(response.items().get(0).runtime()).isNotNull();
        assertThat(response.items().get(0).runtime().rendererKey()).isEqualTo("READ_ALOUD_V1");
        // sampleItemRequest(): speaking=9, writing/reading/listening=0 -> (9+0+0+0)/4 = 2.25
        assertThat(response.items().get(0).overallWeight()).isEqualByComparingTo("2.25");
    }

    @Test
    void replaceItems_computesOverallWeightAsMeanOfFourSkillWeights_notFromRequest() {
        UUID publicId = UUID.randomUUID();
        ScoreTemplate draft = fullyValidTemplate(publicId, "CUSTOM", 1, ScoreTemplateStatus.DRAFT);
        when(repository.findWithItemsByPublicId(publicId)).thenReturn(Optional.of(draft));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new ScoreTemplateItemRequest("SUMMARIZE_SPOKEN_TEXT", "LISTENING", 0, 1, 1, 0, 600,
                BigDecimal.ZERO, BigDecimal.valueOf(23), BigDecimal.ZERO, BigDecimal.valueOf(10));
        // Padded so every column still totals exactly 100.
        var padding = paddingItemRequest("WRITE_ESSAY", BigDecimal.valueOf(100), BigDecimal.valueOf(77),
                BigDecimal.valueOf(100), BigDecimal.valueOf(90));

        ScoreTemplateResponse response = service.replaceItems(
                publicId, new ReplaceScoreTemplateItemsRequest("Custom", List.of(request, padding)));

        // (0 + 23 + 0 + 10) / 4 = 8.25 — matches the APEUni V5 table's SUMMARIZE_SPOKEN_TEXT row exactly.
        assertThat(response.items().get(0).overallWeight()).isEqualByComparingTo("8.25");
    }

    @Test
    void replaceItems_skillWeightsNotSummingTo100_throws() {
        UUID publicId = UUID.randomUUID();
        ScoreTemplate draft = fullyValidTemplate(publicId, "CUSTOM", 1, ScoreTemplateStatus.DRAFT);
        when(repository.findWithItemsByPublicId(publicId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.replaceItems(
                publicId, new ReplaceScoreTemplateItemsRequest("Custom", List.of(sampleItemRequest()))))
                .isInstanceOf(ScoreTemplateValidationException.class)
                .hasMessageContaining("SPEAKING");
        verify(repository, never()).save(any());
    }

    @Test
    void replaceItems_unknownTaskType_throwsValidationException() {
        UUID publicId = UUID.randomUUID();
        ScoreTemplate draft = fullyValidTemplate(publicId, "CUSTOM", 1, ScoreTemplateStatus.DRAFT);
        when(repository.findWithItemsByPublicId(publicId)).thenReturn(Optional.of(draft));
        var badRequest = new ScoreTemplateItemRequest("NOT_A_REAL_TASK_TYPE", "SPEAKING", 0, 1, 1, 0, 30,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThatThrownBy(() -> service.replaceItems(publicId, new ReplaceScoreTemplateItemsRequest("Custom", List.of(badRequest))))
                .isInstanceOf(ScoreTemplateValidationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void createDraft_createsEmptyNextVersion() {
        when(repository.findMaxVersionByCode("CUSTOM")).thenReturn(2);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ScoreTemplateResponse response = service.createDraft(
                new CreateScoreTemplateRequest("CUSTOM", "Custom template"));

        assertThat(response.code()).isEqualTo("CUSTOM");
        assertThat(response.version()).isEqualTo(3);
        assertThat(response.name()).isEqualTo("Custom template");
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.items()).isEmpty();
    }

    @Test
    void deleteDraft_removesOnlyDraftTemplates() {
        UUID publicId = UUID.randomUUID();
        ScoreTemplate draft = fullyValidTemplate(publicId, "CUSTOM", 1, ScoreTemplateStatus.DRAFT);
        when(repository.findWithItemsByPublicId(publicId)).thenReturn(Optional.of(draft));

        service.deleteDraft(publicId);

        verify(repository).delete(draft);
    }

    @Test
    void deleteDraft_onActiveTemplate_throwsNotDraft() {
        UUID publicId = UUID.randomUUID();
        ScoreTemplate active = fullyValidTemplate(publicId, "CUSTOM", 1, ScoreTemplateStatus.ACTIVE);
        when(repository.findWithItemsByPublicId(publicId)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.deleteDraft(publicId))
                .isInstanceOf(ScoreTemplateNotDraftException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void cloneToDraft_copiesAllItemsAndIncrementsVersion() {
        UUID sourceId = UUID.randomUUID();
        ScoreTemplate source = fullyValidTemplate(sourceId, "APEUNI_V5", 3, ScoreTemplateStatus.ACTIVE);
        when(repository.findWithItemsByPublicId(sourceId)).thenReturn(Optional.of(source));
        when(repository.findAllByCodeForUpdate("APEUNI_V5")).thenReturn(List.of(source));
        when(repository.findMaxVersionByCode("APEUNI_V5")).thenReturn(3);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScoreTemplateResponse response = service.cloneToDraft(sourceId);

        assertThat(response.version()).isEqualTo(4);
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.items()).hasSize(source.getItems().size());
    }

    @Test
    void cloneToDraft_repositoryRaceViolation_translatesTo409() {
        UUID sourceId = UUID.randomUUID();
        ScoreTemplate source = fullyValidTemplate(sourceId, "APEUNI_V5", 3, ScoreTemplateStatus.ACTIVE);
        when(repository.findWithItemsByPublicId(sourceId)).thenReturn(Optional.of(source));
        when(repository.findAllByCodeForUpdate("APEUNI_V5")).thenReturn(List.of(source));
        when(repository.findMaxVersionByCode("APEUNI_V5")).thenReturn(3);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.cloneToDraft(sourceId))
                .isInstanceOf(ScoreTemplateConcurrentModificationException.class);
    }

    @Test
    void approvalWorkflow_requiresCatalogAndMovesDraftThroughReview() {
        UUID publicId = UUID.randomUUID();
        ScoreTemplate template = fullyValidTemplate(publicId, "CUSTOM", 1, ScoreTemplateStatus.DRAFT);
        template.getItems().forEach(item -> item.setSection(PteTaskType.valueOf(item.getTaskType()).getSection().name()));
        when(questionTypeService.isActive(any())).thenReturn(true);
        when(repository.findWithItemsByPublicId(publicId)).thenReturn(Optional.of(template));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service = new ScoreTemplateAdminService(repository, questionTypeService);

        ScoreTemplateResponse pending = service.submitApproval(publicId);
        assertThat(pending.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(template.getStatus()).isEqualTo(ScoreTemplateStatus.PENDING_APPROVAL);

        ScoreTemplateResponse approvedForEditing = service.approve(publicId);
        assertThat(approvedForEditing.status()).isEqualTo("DRAFT");
        assertThat(template.getStatus()).isEqualTo(ScoreTemplateStatus.DRAFT);
        verify(questionTypeService, org.mockito.Mockito.times(2)).isActive("READ_ALOUD");
    }

    private ScoreTemplateItemRequest sampleItemRequest() {
        return new ScoreTemplateItemRequest("READ_ALOUD", "SPEAKING", 0, 6, 7, 35, 40,
                BigDecimal.valueOf(9), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
