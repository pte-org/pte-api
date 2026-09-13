package com.pte.scoring.service;

import com.pte.scoring.constant.ScoringConstants;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiScoringTaskCatalogTest {

    private static final List<String> SPEECH_TYPES = List.of(
            ScoringConstants.TASK_TYPE_READ_ALOUD,
            ScoringConstants.TASK_TYPE_REPEAT_SENTENCE,
            ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE,
            ScoringConstants.TASK_TYPE_RE_TELL_LECTURE,
            ScoringConstants.TASK_TYPE_ANSWER_SHORT_QUESTION,
            ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION,
            ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION);

    private static final List<String> TEXT_TYPES = List.of(
            ScoringConstants.TASK_TYPE_WRITE_ESSAY,
            ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT,
            ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT);

    @Test
    void supports_allTenAiTaskTypes() {
        assertThat(SPEECH_TYPES).allSatisfy(taskType -> assertThat(AiScoringTaskCatalog.supports(taskType)).isTrue());
        assertThat(TEXT_TYPES).allSatisfy(taskType -> assertThat(AiScoringTaskCatalog.supports(taskType)).isTrue());
    }

    @Test
    void classifiesSpeechAndTextWithoutOverlap() {
        assertThat(SPEECH_TYPES).allSatisfy(taskType -> {
            assertThat(AiScoringTaskCatalog.isSpeech(taskType)).isTrue();
            assertThat(AiScoringTaskCatalog.isText(taskType)).isFalse();
        });
        assertThat(TEXT_TYPES).allSatisfy(taskType -> {
            assertThat(AiScoringTaskCatalog.isText(taskType)).isTrue();
            assertThat(AiScoringTaskCatalog.isSpeech(taskType)).isFalse();
        });
    }

    @Test
    void rejectsNullUnknownAndUnscoredPersonalIntroduction() {
        assertThat(AiScoringTaskCatalog.supports(null)).isFalse();
        assertThat(AiScoringTaskCatalog.supports("UNKNOWN")).isFalse();
        assertThat(AiScoringTaskCatalog.supports(ScoringConstants.TASK_TYPE_PERSONAL_INTRODUCTION)).isFalse();
    }
}
