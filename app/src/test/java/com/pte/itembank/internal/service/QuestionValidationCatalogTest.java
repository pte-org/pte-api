package com.pte.itembank.internal.service;

import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionType;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.QuestionValidationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionValidationCatalogTest {

    @Mock
    private QuestionTypeService questionTypeService;

    @Test
    void validation_usesPersistedRequirements_insteadOfEnumFlags() {
        QuestionType definition = new QuestionType();
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

    @Test
    void referenceAnswer_isRequiredForAnyTypeThatTheCatalogFlags() {
        Question question = question(PteTaskType.READ_ALOUD, "Flagged read aloud");
        question.setPromptText("Some text");
        stubDefinition(PteTaskType.READ_ALOUD, definition -> definition.setRequiresReferenceAnswer(true));

        assertThatThrownBy(() -> new QuestionValidationHelper(questionTypeService).validate(question))
                .isInstanceOf(QuestionValidationException.class)
                .hasMessage(ItembankConstants.REFERENCE_ANSWER_REQUIRED);

        question.setReferenceAnswerText("Some text");
        assertThatCode(() -> new QuestionValidationHelper(questionTypeService).validate(question))
                .doesNotThrowAnyException();
    }

    @Test
    void referenceAnswer_isNotRequiredWhenTheCatalogClearsTheFlag() {
        Question question = question(PteTaskType.REPEAT_SENTENCE, "Unflagged repeat sentence");
        question.setAudioPromptRef(UUID.randomUUID());
        stubDefinition(PteTaskType.REPEAT_SENTENCE, definition -> {
            definition.setRequiresAudioPrompt(true);
            definition.setRequiresReferenceAnswer(false);
        });

        assertThatCode(() -> new QuestionValidationHelper(questionTypeService).validate(question))
                .doesNotThrowAnyException();
    }

    @Test
    void promptText_isNotRequiredForSummarizeSpokenTextWhenTheCatalogClearsTheFlag() {
        Question question = question(PteTaskType.SUMMARIZE_SPOKEN_TEXT, "Unflagged summarize spoken text");
        stubDefinition(PteTaskType.SUMMARIZE_SPOKEN_TEXT, definition -> definition.setRequiresPromptText(false));

        assertThatCode(() -> new QuestionValidationHelper(questionTypeService).validate(question))
                .doesNotThrowAnyException();
    }

    @Test
    void promptText_isRequiredForSummarizeSpokenTextWhenTheCatalogSetsTheFlag() {
        Question question = question(PteTaskType.SUMMARIZE_SPOKEN_TEXT, "Flagged summarize spoken text");
        stubDefinition(PteTaskType.SUMMARIZE_SPOKEN_TEXT, definition -> definition.setRequiresPromptText(true));

        assertThatThrownBy(() -> new QuestionValidationHelper(questionTypeService).validate(question))
                .isInstanceOf(QuestionValidationException.class)
                .hasMessage(ItembankConstants.PROMPT_TEXT_REQUIRED);
    }

    private void stubDefinition(PteTaskType type, java.util.function.Consumer<QuestionType> configure) {
        QuestionType definition = new QuestionType();
        definition.setCode(type.name());
        definition.setSection(type.getSection());
        configure.accept(definition);
        when(questionTypeService.findDefinitionByCode(type.name())).thenReturn(Optional.of(definition));
    }

    private static Question question(PteTaskType type, String title) {
        Question question = new Question();
        question.setPteTaskType(type);
        question.setTitle(title);
        return question;
    }
}
