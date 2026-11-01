package com.pte.assessment.internal.service.definition;

import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.ExamDefinitionItem;
import com.pte.assessment.internal.exception.ExamDefinitionValidationException;
import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.TaskAuthoringRequirements;
import com.pte.itembank.TaskRuntimeContractDescriptor;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

    @Test
    void aDefaultTemplateMissingACatalogTaskTypeIsRejected() {
        when(questionTypeService.listActiveTaskTypeKeys()).thenReturn(List.of("READ_ALOUD", "WRITE_ESSAY"));

        assertThatThrownBy(() -> new ExamDefinitionValidator(questionTypeService, null, null)
                .validateIncludesWholeCatalog(template(false, "READ_ALOUD")))
                .isInstanceOf(ExamDefinitionValidationException.class)
                .hasMessageContaining("WRITE_ESSAY");
    }

    @Test
    void aDefaultTemplateHoldingTheWholeCatalogIsAccepted() {
        when(questionTypeService.listActiveTaskTypeKeys()).thenReturn(List.of("READ_ALOUD", "WRITE_ESSAY"));

        assertThatCode(() -> new ExamDefinitionValidator(questionTypeService, null, null)
                .validateIncludesWholeCatalog(template(false, "READ_ALOUD", "WRITE_ESSAY")))
                .doesNotThrowAnyException();
    }

    @Test
    void aHostTemplateMayOmitCatalogTaskTypes() {
        assertThatCode(() -> new ExamDefinitionValidator(questionTypeService, null, null)
                .validateIncludesWholeCatalog(template(true, "READ_ALOUD")))
                .doesNotThrowAnyException();
    }

    @Test
    void aHostTemplateCannotUseATaskTypeOutsideItsBase() {
        ExamDefinitionValidator validator = new ExamDefinitionValidator(questionTypeService, null, null);

        assertThatThrownBy(() -> validator.validateWithinBase(template(true, "READ_ALOUD", "WRITE_ESSAY"),
                template(false, "READ_ALOUD")))
                .isInstanceOf(ExamDefinitionValidationException.class)
                .hasMessageContaining("WRITE_ESSAY");
        assertThatCode(() -> validator.validateWithinBase(template(true, "READ_ALOUD"),
                template(false, "READ_ALOUD", "WRITE_ESSAY"))).doesNotThrowAnyException();
    }

    private ExamDefinition template(boolean hostOwned, String... taskTypes) {
        ExamDefinition template = new ExamDefinition();
        if (hostOwned) {
            template.setTenantId(java.util.UUID.randomUUID());
        }
        for (int i = 0; i < taskTypes.length; i++) {
            ExamDefinitionItem item = new ExamDefinitionItem();
            item.setTaskType(taskTypes[i]);
            item.setSequence(i);
            template.addItem(item);
        }
        return template;
    }
}
