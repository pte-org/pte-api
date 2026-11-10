package com.pte.itembank.internal.generation.draft.audio;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each listening type is stored the way its task-type rules, the exam client and the scorers expect: the transcript
 * in referenceAnswerText (never shown to the candidate), the candidate-facing text in promptText, and the answer in
 * correctAnswerText where the task is scored against text.
 */
class AudioDraftMapperTest {

    private static final String TRANSCRIPT = "Plants need sunlight and water to grow well.";

    private final AudioDraftMapper mapper = new AudioDraftMapper();

    private static ItemDraft.ListeningDraft listening(String question, List<String> options, List<Integer> correct,
                                                      List<ItemDraft.ListeningDraft.Slot> blanks, String answer) {
        return new ItemDraft.ListeningDraft("topic", TRANSCRIPT, question, options, correct, blanks, answer);
    }

    @Test
    void writeFromDictationKeepsTheSentenceAsTheAnswerAndNothingIsShown() {
        CreateQuestionRequest request = mapper.mapWriteFromDictation(listening(null, null, null, null, null),
                CefrLevel.B2, QuestionPool.PRACTICE);

        assertThat(request.correctAnswerText()).isEqualTo(TRANSCRIPT);
        assertThat(request.referenceAnswerText()).isEqualTo(TRANSCRIPT);
        assertThat(request.promptText()).isNull();
    }

    @Test
    void repeatSentenceKeepsTheSentenceAsTheReferenceOnly() {
        CreateQuestionRequest request = mapper.mapRepeatSentence(listening(null, null, null, null, null),
                CefrLevel.B2, QuestionPool.PRACTICE);

        assertThat(request.referenceAnswerText()).isEqualTo(TRANSCRIPT);
        assertThat(request.promptText()).isNull();
        assertThat(request.correctAnswerText()).isNull();
    }

    @Test
    void answerShortQuestionStoresTheAcceptedAnswersAsTheCorrectAnswer() {
        CreateQuestionRequest request = mapper.mapAnswerShortQuestion(
                listening(null, null, null, null, "wind power / wind energy"), CefrLevel.B2, QuestionPool.PRACTICE);

        assertThat(request.correctAnswerText()).isEqualTo("wind power / wind energy");
        assertThat(request.referenceAnswerText()).isEqualTo(TRANSCRIPT);
        assertThat(request.promptText()).isNull();
    }

    @Test
    void summarizeSpokenTextNeedsAPromptAndAWordRange() {
        CreateQuestionRequest request = mapper.mapSummarizeSpokenText(listening(null, null, null, null, null),
                CefrLevel.B2, QuestionPool.PRACTICE);

        assertThat(request.promptText()).isEqualTo(AudioDraftMapper.SUMMARIZE_SPOKEN_TEXT_INSTRUCTION);
        assertThat(request.minWordCount()).isEqualTo(50);
        assertThat(request.maxWordCount()).isEqualTo(70);
        assertThat(request.referenceAnswerText()).isEqualTo(TRANSCRIPT);
    }

    @Test
    void multipleChoiceListeningFlagsTheCorrectOptionsAndKeepsTheTranscriptHidden() {
        CreateQuestionRequest request = mapper.mapMcListeningMultiple(
                listening("What is said?", List.of("a", "b", "c", "d", "e"), List.of(1, 3), null, null),
                CefrLevel.B2, QuestionPool.PRACTICE);

        assertThat(request.options()).hasSize(5);
        assertThat(request.options().get(1).correct()).isTrue();
        assertThat(request.options().get(3).correct()).isTrue();
        assertThat(request.options().get(0).correct()).isFalse();
        assertThat(request.promptText()).isEqualTo("What is said?");
        assertThat(request.referenceAnswerText()).isEqualTo(TRANSCRIPT);
    }

    @Test
    void fillBlanksShowsNumberedGapsAndStoresTheAnswersAsAJsonList() {
        var blanks = List.of(new ItemDraft.ListeningDraft.Slot("sunlight", null),
                new ItemDraft.ListeningDraft.Slot("water", null));
        CreateQuestionRequest request = mapper.mapListeningFillBlanks(listening(null, null, null, blanks, null),
                CefrLevel.B2, QuestionPool.PRACTICE);

        assertThat(request.promptText()).isEqualTo("Plants need {{0}} and {{1}} to grow well.");
        assertThat(request.correctAnswerText()).isEqualTo("[\"sunlight\",\"water\"]");
        assertThat(request.referenceAnswerText()).isEqualTo(TRANSCRIPT);
        assertThat(request.options()).isNull();
    }

    @Test
    void gapsFollowTheOrderOfTheAnswersAndSkipAnAnswerThatIsNotInTheTranscript() {
        assertThat(AudioDraftMapper.withGaps("one two three two", List.of("two", "missing", "two")))
                .isEqualTo("one {{0}} three {{1}}");
    }

    @Test
    void jsonListEscapesQuotes() {
        assertThat(AudioDraftMapper.jsonList(List.of("a\"b", "c"))).isEqualTo("[\"a\\\"b\",\"c\"]");
    }
}
