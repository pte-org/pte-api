package com.pte.assessment.internal.service.definition;

import com.pte.assessment.internal.exception.ExamDefinitionValidationException;
import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.TaskAuthoringRequirements;
import com.pte.itembank.TaskRuntimeContractDescriptor;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExamDefinitionValidatorTest {

    private final QuestionTypeService questionTypeService = mock(QuestionTypeService.class);

    @Test
    void aCustomTaskTypeGetsItsProfileFromTheRuntimeContractOfItsQuestionType() {
        TaskRuntimeContractDescriptor contract = new TaskRuntimeContractDescriptor("CUSTOM_SCREEN_V1", 3,
                "CUSTOM_PROFILE", 2, "BEHAVIOR", "RENDERER", 4, "SCORING", 5, "AI", List.of("AUDIO_PLAYBACK"),
                "1.0.0", "AUTHORING", 6, new TaskAuthoringRequirements(false, false, false, false, false,
                        false, false, false, false), "ACTIVE");
        when(questionTypeService.resolveRuntimeContract("CUSTOM_LISTEN")).thenReturn(contract);

        TaskRuntimeProfileDescriptor profile = new ExamDefinitionValidator(questionTypeService, null, null)
                .resolveActiveProfile("CUSTOM_LISTEN");

        assertThat(profile.taskTypeCode()).isEqualTo("CUSTOM_LISTEN");
        assertThat(profile.profileKey()).isEqualTo("CUSTOM_PROFILE");
        assertThat(profile.profileVersion()).isEqualTo(2);
        assertThat(profile.screenKey()).isEqualTo("CUSTOM_SCREEN_V1");
        assertThat(profile.contractVersion()).isEqualTo(3);
    }

    @Test
    void aCustomTaskTypeWithoutAQuestionTypeCatalogIsRejected() {
        assertThatThrownBy(() -> new ExamDefinitionValidator(null, null, null).resolveActiveProfile("CUSTOM_LISTEN"))
                .isInstanceOf(ExamDefinitionValidationException.class);
    }

    @Test
    void aStandardTaskTypeFallsBackToTheBuiltInRegistry() {
        TaskRuntimeProfileDescriptor profile = new ExamDefinitionValidator(questionTypeService, null, null)
                .resolveActiveProfile("READ_ALOUD");

        assertThat(profile.taskTypeCode()).isEqualTo("READ_ALOUD");
    }
}
