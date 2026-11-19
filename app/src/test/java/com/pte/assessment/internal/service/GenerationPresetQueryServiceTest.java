package com.pte.assessment.internal.service;

import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.dto.response.GenerationPresetResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerationPresetQueryServiceTest {

    private final ExamDefinitionService examDefinitions = mock(ExamDefinitionService.class);
    private final GenerationPresetQueryService service = new GenerationPresetQueryService(examDefinitions);

    private static ExamDefinitionItemResponse item(String taskType, String section, int sequence, int maxCount) {
        return new ExamDefinitionItemResponse(taskType, section, sequence, 0, maxCount, 0, 0, "AUTO",
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private void activeTemplate(List<ExamDefinitionItemResponse> items) {
        ExamDefinitionResponse template = mock(ExamDefinitionResponse.class);
        when(template.items()).thenReturn(items);
        when(examDefinitions.findActive()).thenReturn(Optional.of(template));
    }

    @Test
    void theFullPresetMatchesTheActiveTemplateExactlyInTemplateOrder() {
        activeTemplate(List.of(
                item("READ_ALOUD", "SPEAKING", 1, 7),
                item("WRITE_ESSAY", "WRITING", 3, 2),
                item("REPEAT_SENTENCE", "SPEAKING", 2, 11),
                item("MC_READING_SINGLE", "READING", 4, 2)));

        assertThat(service.listActivePresets()).containsExactly(
                new GenerationPresetResponse("READ_ALOUD", "SPEAKING", 7),
                new GenerationPresetResponse("REPEAT_SENTENCE", "SPEAKING", 11),
                new GenerationPresetResponse("WRITE_ESSAY", "WRITING", 2),
                new GenerationPresetResponse("MC_READING_SINGLE", "READING", 2));
    }

    @Test
    void aTaskTypeListedTwiceKeepsTheLargerCountAndItsFirstPosition() {
        activeTemplate(List.of(item("WRITE_ESSAY", "WRITING", 1, 1), item("READ_ALOUD", "SPEAKING", 2, 6),
                item("WRITE_ESSAY", "WRITING", 3, 2)));

        assertThat(service.listActivePresets()).containsExactly(
                new GenerationPresetResponse("WRITE_ESSAY", "WRITING", 2),
                new GenerationPresetResponse("READ_ALOUD", "SPEAKING", 6));
    }

    @Test
    void rowsWithoutItemsAreLeftOut() {
        activeTemplate(List.of(item("READ_ALOUD", "SPEAKING", 1, 0), item("WRITE_ESSAY", "WRITING", 2, 1)));

        assertThat(service.listActivePresets()).extracting(GenerationPresetResponse::taskType)
                .containsExactly("WRITE_ESSAY");
    }

    @Test
    void noActiveTemplateAnswersAnEmptyList() {
        when(examDefinitions.findActive()).thenReturn(Optional.empty());

        assertThat(service.listActivePresets()).isEmpty();
    }
}
