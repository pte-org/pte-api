package com.pte.assessment.internal.service;

import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.dto.response.ResolvedExamItemResponse;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.assessment.internal.config.TaskTimingConfig;
import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.TaskRuntimeContractConstants;
import com.pte.media.MediaService;
import com.pte.media.dto.response.PresignedDownloadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamItemRuntimeResolverTest {

    private static final UUID TEMPLATE_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-29T04:00:00Z");

    @Mock
    private MediaService mediaService;
    @Mock
    private TaskTimingConfig taskTimingConfig;
    @Mock
    private ExamDefinitionService examDefinitionService;
    @Mock
    private QuestionTypeService questionTypeService;

    private ExamItemRuntimeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ExamItemRuntimeResolver(mediaService, taskTimingConfig, examDefinitionService,
                questionTypeService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void audioPromptTimingMatchesStudentFormulaAndPreservesFrozenIdentity() {
        UUID snapshotId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID sourceQuestionId = UUID.randomUUID();
        UUID audioId = UUID.randomUUID();

        when(examDefinitionService.getByPublicId(TEMPLATE_ID)).thenReturn(
                new ExamDefinitionResponse(TEMPLATE_ID, "PTE", 1, "PTE", "ACTIVE",
                        List.of(templateItem("REPEAT_SENTENCE", "SPEAKING", 4, 15))));
        when(taskTimingConfig.timingForIfConfigured("REPEAT_SENTENCE"))
                .thenReturn(new TaskTimingConfig.Timing(0, 0, 3, null));
        when(mediaService.presignGet(eq(audioId), anyLong(), eq(TENANT_ID)))
                .thenReturn(new PresignedDownloadResponse("https://media.test/audio", 300, 6));

        SnapshotContentResponse content = new SnapshotContentResponse(snapshotId, "Preview", 2, TEMPLATE_ID,
                TENANT_ID, List.of(new SnapshotContentResponse.Item(
                        0, "SPEAKING", "REPEAT_SENTENCE", "Repeat", "Listen", audioId, null,
                        "reference", "correct", null, null, null, "REPEAT_SENTENCE", "Repeat sentence",
                        "REPEAT_SENTENCE", null, TaskRuntimeContractConstants.MAPPING_VERSION_LEGACY,
                        TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_LEGACY, sourceQuestionId, itemId)));

        List<ResolvedExamItemResponse> resolved = resolver.resolve(content, 300, TENANT_ID);

        assertThat(resolved).hasSize(1);
        ResolvedExamItemResponse item = resolved.get(0);
        assertThat(item.snapshotItemPublicId()).isEqualTo(itemId);
        assertThat(item.sourceQuestionPublicId()).isEqualTo(sourceQuestionId);
        assertThat(item.preListenSeconds()).isEqualTo(3);
        assertThat(item.preRecordSeconds()).isEqualTo(4);
        assertThat(item.audioDurationSeconds()).isEqualTo(6);
        assertThat(item.prepSeconds()).isEqualTo(13);
        assertThat(item.responseSeconds()).isEqualTo(15);
        assertThat(item.audioUrlExpiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(item.referenceAnswerText()).isEqualTo("reference");
        verify(mediaService).presignGet(audioId, 300, TENANT_ID);
    }

    @Test
    void staticDefinitionTimingIsResolvedWithoutFallbackGuess() {
        UUID snapshotId = UUID.randomUUID();
        when(examDefinitionService.getByPublicId(TEMPLATE_ID)).thenReturn(
                new ExamDefinitionResponse(TEMPLATE_ID, "PTE", 1, "PTE", "ACTIVE",
                        List.of(templateItem("MC_READING_SINGLE", "READING", 7, 42))));

        SnapshotContentResponse content = new SnapshotContentResponse(snapshotId, "Preview", 1, TEMPLATE_ID,
                TENANT_ID, List.of(new SnapshotContentResponse.Item(
                        0, "READING", "MC_READING_SINGLE", "Question", "Prompt", null, null,
                        "reference", "correct", null, null, "[{\"orderIndex\":0,\"text\":\"A\"}]",
                        "MC_READING_SINGLE", "Multiple choice", "MC_READING_SINGLE", null, null, null,
                        UUID.randomUUID(), UUID.randomUUID())));

        ResolvedExamItemResponse item = resolver.resolve(content, 300, TENANT_ID).get(0);

        assertThat(item.prepSeconds()).isEqualTo(7);
        assertThat(item.responseSeconds()).isEqualTo(42);
        assertThat(item.preListenSeconds()).isNull();
        assertThat(item.preRecordSeconds()).isNull();
    }

    @Test
    void instructionsAreLookedUpOnceForTheWholeExamAndMissingOnesStayNull() {
        when(examDefinitionService.getByPublicId(TEMPLATE_ID)).thenReturn(
                new ExamDefinitionResponse(TEMPLATE_ID, "PTE", 1, "PTE", "ACTIVE", List.of(
                        templateItem("MC_READING_SINGLE", "READING", 7, 42),
                        templateItem("RE_ORDER_PARAGRAPHS", "READING", 0, 60))));
        when(questionTypeService.findInstructionsByTaskTypeKeys(
                Set.of("MC_READING_SINGLE", "RE_ORDER_PARAGRAPHS")))
                .thenReturn(Map.of("MC_READING_SINGLE", "Select the correct response."));

        SnapshotContentResponse content = new SnapshotContentResponse(UUID.randomUUID(), "Preview", 1,
                TEMPLATE_ID, TENANT_ID, List.of(
                        readingItem(0, "MC_READING_SINGLE"),
                        readingItem(1, "MC_READING_SINGLE"),
                        readingItem(2, "RE_ORDER_PARAGRAPHS")));

        List<ResolvedExamItemResponse> resolved = resolver.resolve(content, 300, TENANT_ID);

        assertThat(resolved).extracting(ResolvedExamItemResponse::taskTypeInstruction).containsExactly(
                "Select the correct response.", "Select the correct response.", null);
        verify(questionTypeService).findInstructionsByTaskTypeKeys(
                Set.of("MC_READING_SINGLE", "RE_ORDER_PARAGRAPHS"));
    }

    private SnapshotContentResponse.Item readingItem(int orderIndex, String taskType) {
        return new SnapshotContentResponse.Item(
                orderIndex, "READING", taskType, "Question", "Prompt", null, null,
                null, null, null, null, "[]", taskType, taskType, taskType, null, null, null,
                UUID.randomUUID(), UUID.randomUUID());
    }

    private ExamDefinitionItemResponse templateItem(String taskType, String section, int prepSeconds,
            int responseSeconds) {
        return new ExamDefinitionItemResponse(taskType, section, 0, 1, 1, prepSeconds, responseSeconds,
                "OBJECTIVE", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
