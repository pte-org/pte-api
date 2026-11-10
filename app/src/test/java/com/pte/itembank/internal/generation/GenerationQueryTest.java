package com.pte.itembank.internal.generation;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.draft.DraftParser;
import com.pte.itembank.internal.generation.draft.DraftToQuestionMapper;
import com.pte.itembank.internal.generation.dto.GenerationTaskTypeResponse;
import com.pte.itembank.internal.generation.job.GenerationTaskTypeCatalog;
import com.pte.itembank.internal.generation.llm.LlmGateway;
import com.pte.itembank.internal.generation.llm.LlmItemGenerator;
import com.pte.itembank.internal.generation.prompt.PromptTestRegistry;
import com.pte.itembank.internal.generation.validate.DraftValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** The task types the form may offer are exactly those the real generator has a prompt for. */
class GenerationQueryTest {

    private static final Set<String> TEXT_TYPES = Set.of("MC_READING_SINGLE", "MC_READING_MULTIPLE",
            "FILL_IN_THE_BLANKS_DROPDOWN", "FILL_IN_THE_BLANKS_DRAG_AND_DROP", "RE_ORDER_PARAGRAPHS",
            "SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY", "READ_ALOUD");
    private static final Set<String> AUDIO_TYPES = Set.of("WRITE_FROM_DICTATION", "REPEAT_SENTENCE",
            "ANSWER_SHORT_QUESTION", "SUMMARIZE_SPOKEN_TEXT", "MC_LISTENING_SINGLE", "MC_LISTENING_MULTIPLE",
            "HIGHLIGHT_CORRECT_SUMMARY", "SELECT_MISSING_WORD", "HIGHLIGHT_INCORRECT_WORDS",
            "FILL_IN_THE_BLANKS_TYPE_IN", "RE_TELL_LECTURE", "SUMMARIZE_GROUP_DISCUSSION", "RESPOND_TO_A_SITUATION");

    private final GenerationTaskTypeCatalog catalog = new GenerationTaskTypeCatalog(List.of(realGenerator()));

    private static LlmItemGenerator realGenerator() {
        return new LlmItemGenerator(mock(LlmGateway.class), PromptTestRegistry.create(), mock(DraftParser.class),
                mock(DraftValidator.class), mock(DraftToQuestionMapper.class));
    }

    private Map<String, GenerationTaskTypeResponse> byName() {
        return catalog.supported().stream()
                .collect(java.util.stream.Collectors.toMap(GenerationTaskTypeResponse::taskType, t -> t));
    }

    @Test
    void offersTextAudioAndImageTypes() {
        Set<String> expected = new java.util.HashSet<>(TEXT_TYPES);
        expected.addAll(AUDIO_TYPES);
        expected.add("DESCRIBE_IMAGE");

        assertThat(byName().keySet()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void textTypesNeedNoMediaAndUseTheTextEstimate() {
        Map<String, GenerationTaskTypeResponse> types = byName();
        for (String name : TEXT_TYPES) {
            assertThat(types.get(name).needsAudio()).as(name).isFalse();
            assertThat(types.get(name).needsImage()).as(name).isFalse();
            assertThat(types.get(name).estimatedSecondsPerItem())
                    .isEqualTo(QuestionGenerationConstants.ESTIMATED_SECONDS_TEXT);
        }
    }

    @Test
    void audioAndImageTypesAreFlaggedAndUseTheMediaEstimate() {
        Map<String, GenerationTaskTypeResponse> types = byName();
        assertThat(types.get("WRITE_FROM_DICTATION").needsAudio()).isTrue();
        assertThat(types.get("SUMMARIZE_GROUP_DISCUSSION").needsAudio()).isTrue();
        assertThat(types.get("DESCRIBE_IMAGE").needsImage()).isTrue();
        assertThat(types.get("WRITE_FROM_DICTATION").estimatedSecondsPerItem())
                .isEqualTo(QuestionGenerationConstants.ESTIMATED_SECONDS_MEDIA);
    }

    @Test
    void findIsCaseInsensitiveAndAcceptsOnlyOfferedTypes() {
        assertThat(catalog.find("write_essay")).isPresent();
        assertThat(catalog.find(" READ_ALOUD ")).isPresent();
        assertThat(catalog.find("PERSONAL_INTRODUCTION")).isEmpty();
        assertThat(catalog.find("NOT_A_TASK_TYPE")).isEmpty();
        assertThat(catalog.find(" ")).isEmpty();
        assertThat(catalog.find(null)).isEmpty();
    }
}
