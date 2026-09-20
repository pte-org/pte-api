package com.pte.scoretemplate.internal.service;

import com.pte.scoretemplate.domain.enums.ScoringMethod;
import com.pte.scoretemplate.internal.exception.ScoreTemplateValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cross-checked cell-by-cell against the APEUni V5 seed table (mirrors {@code ScoreTemplateSeedMigrationTest}). */
class TaskTypeScoringMethodsTest {

    @ParameterizedTest
    @CsvSource({
            "READ_ALOUD, AI_SPEECH",
            "REPEAT_SENTENCE, AI_SPEECH",
            "DESCRIBE_IMAGE, AI_SPEECH",
            "RE_TELL_LECTURE, AI_SPEECH",
            "ANSWER_SHORT_QUESTION, AI_SPEECH",
            "SUMMARIZE_GROUP_DISCUSSION, AI_SPEECH",
            "RESPOND_TO_A_SITUATION, AI_SPEECH",
            "SUMMARIZE_WRITTEN_TEXT, AI_TEXT",
            "WRITE_ESSAY, AI_TEXT",
            "FILL_IN_THE_BLANKS_DROPDOWN, OBJECTIVE",
            "MC_READING_MULTIPLE, OBJECTIVE",
            "RE_ORDER_PARAGRAPHS, OBJECTIVE",
            "FILL_IN_THE_BLANKS_DRAG_AND_DROP, OBJECTIVE",
            "MC_READING_SINGLE, OBJECTIVE",
            "SUMMARIZE_SPOKEN_TEXT, AI_TEXT",
            "MC_LISTENING_MULTIPLE, OBJECTIVE",
            "FILL_IN_THE_BLANKS_TYPE_IN, OBJECTIVE",
            "HIGHLIGHT_CORRECT_SUMMARY, OBJECTIVE",
            "MC_LISTENING_SINGLE, OBJECTIVE",
            "SELECT_MISSING_WORD, OBJECTIVE",
            "HIGHLIGHT_INCORRECT_WORDS, OBJECTIVE",
            "WRITE_FROM_DICTATION, OBJECTIVE",
    })
    void resolve_everyRequiredTaskType_matchesV5Table(String taskType, ScoringMethod expected) {
        assertThat(TaskTypeScoringMethods.resolve(taskType)).isEqualTo(expected);
    }

    @Test
    void resolve_unknownTaskType_throws() {
        assertThatThrownBy(() -> TaskTypeScoringMethods.resolve("NOT_A_REAL_TASK_TYPE"))
                .isInstanceOf(ScoreTemplateValidationException.class);
    }
}
