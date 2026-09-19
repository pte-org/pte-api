package com.pte.itembank.internal.service;

import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionTypeDefinition;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionValidationCatalogTest {

    @Mock
    private QuestionTypeService questionTypeService;

    @Test
    void validation_usesPersistedRequirements_insteadOfEnumFlags() {
        QuestionTypeDefinition definition = new QuestionTypeDefinition();
        definition.setCode(PteTaskType.READ_ALOUD.name());
        definition.setSection(PteSection.SPEAKING);
        definition.setRequiresPromptText(false);
        when(questionTypeService.findDefinitionByCode(PteTaskType.READ_ALOUD.name()))
                .thenReturn(Optional.of(definition));

        Question question = new Question();
        question.setPteTaskType(PteTaskType.READ_ALOUD);
        question.setTitle("Configured without prompt");

        assertThatCode(() -> new QuestionValidationHelper(questionTypeService).validate(question))
                .doesNotThrowAnyException();
    }
}
