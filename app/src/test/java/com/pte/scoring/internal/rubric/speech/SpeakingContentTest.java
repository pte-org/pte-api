package com.pte.scoring.internal.rubric.speech;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.rubric.RubricScorer;
import com.pte.scoring.rubric.TraitScoreSet;
import com.pte.scoring.support.SpeakingRubrics;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Read Aloud and Repeat Sentence Content counted in code (Score Guide pp.15-17; plan phase 5, L6 and L7). */
class SpeakingContentTest {

    // ---- Read Aloud ----

    @Test
    void readAloudContentIsTheWordCountWhenEveryWordIsRead() {
        ReadAloudContent content = ReadAloudContent.measure("The sun rises in the east.", "the sun rises in the east");

        assertThat(content.wordCount()).isEqualTo(6);
        assertThat(content.achieved()).isEqualTo(6);
        assertThat(content.replacements() + content.omissions() + content.insertions()).isZero();
    }

    @Test
    void anInsertedWordIsAnErrorToo() {
        ReadAloudContent content = ReadAloudContent.measure("The sun rises in the east.",
                "the big sun rises in the east");

        assertThat(content.insertions()).isEqualTo(1);
        assertThat(content.achieved()).isEqualTo(5);
    }

    @Test
    void replacementsOmissionsAndInsertionsAreCountedSeparately() {
        ReadAloudContent content = ReadAloudContent.measure("one two three four five six",
                "one too three five six seven");

        assertThat(content.replacements()).isEqualTo(1); // two -> too
        assertThat(content.omissions()).isEqualTo(1);    // four
        assertThat(content.insertions()).isEqualTo(1);   // seven
        assertThat(content.achieved()).isEqualTo(3);
        assertThat(content.detail()).containsEntry("wordCount", 6).containsEntry("achieved", 3);
    }

    @Test
    void hesitationsAreIgnored() {
        ReadAloudContent content = ReadAloudContent.measure("The sun rises in the east.",
                "the uh sun um rises in the er east");

        assertThat(content.achieved()).isEqualTo(6);
    }

    @Test
    void contentNeverGoesBelowZero() {
        ReadAloudContent content = ReadAloudContent.measure("one two",
                "alpha beta gamma delta epsilon zeta eta theta");

        assertThat(content.achieved()).isZero();
        assertThat(content.wordCount()).isEqualTo(2);
    }

    @Test
    void silenceOmitsEveryWord() {
        ReadAloudContent content = ReadAloudContent.measure("one two three", "");

        assertThat(content.omissions()).isEqualTo(3);
        assertThat(content.achieved()).isZero();
    }

    /** Golden N = 1, 30, 80 with one error in ten words (none for N = 1): (N - errors + P + F) / (N + 10). */
    @Test
    void readAloudItemScoresForPassagesOfOneThirtyAndEightyWords() {
        assertThat(readAloudItem(1, 0, 4, 4)).isEqualTo(82);   // (1 + 4 + 4) / 11 = 81.8
        assertThat(readAloudItem(30, 3, 4, 4)).isEqualTo(88);  // (27 + 8) / 40 = 87.5
        assertThat(readAloudItem(80, 8, 4, 4)).isEqualTo(89);  // (72 + 8) / 90 = 88.9
    }

    @Test
    void aPassageWithoutWordsIsABrokenItemNotAScore() {
        assertThatThrownBy(() -> ReadAloudContent.measure(" ... ", "anything"))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> assertThat(ex.getMessage())
                        .isEqualTo(ScoringConstants.SPEECH_REFERENCE_TEXT_EMPTY));
    }

    // ---- Repeat Sentence ----

    @Test
    void repeatSentenceBandsFollowTheFiftyPercentThreshold() {
        String sentence = "one two three four five six seven eight nine ten";

        assertThat(RepeatSentenceContent.measure(sentence, sentence).band()).isEqualTo(3);
        assertThat(RepeatSentenceContent.measure(sentence, "one two three four five").band()).isEqualTo(2);
        assertThat(RepeatSentenceContent.measure(sentence, "one two three four five x y z").band()).isEqualTo(2);
        assertThat(RepeatSentenceContent.measure(sentence, "one two three four").band()).isEqualTo(1);
        assertThat(RepeatSentenceContent.measure(sentence, "one").band()).isEqualTo(1);
        assertThat(RepeatSentenceContent.measure(sentence, "alpha beta").band()).isZero();
        assertThat(RepeatSentenceContent.measure(sentence, "").band()).isZero();
    }

    @Test
    void wordsOutOfSequenceDoNotCountAndHesitationsAreIgnored() {
        String sentence = "one two three four";

        assertThat(RepeatSentenceContent.measure(sentence, "um one uh two three four").band()).isEqualTo(3);
        RepeatSentenceContent reversed = RepeatSentenceContent.measure(sentence, "four three two one");
        assertThat(reversed.wordsInSequence()).isEqualTo(1);
        assertThat(reversed.band()).isEqualTo(1);
    }

    @Test
    void aSentenceWithoutWordsIsABrokenItem() {
        assertThatThrownBy(() -> RepeatSentenceContent.measure("", "one"))
                .isInstanceOf(AiProviderException.class);
    }

    private static int readAloudItem(int wordCount, int errors, int pronunciation, int fluency) {
        String passage = IntStream.range(0, wordCount).mapToObj(i -> "w" + i).collect(Collectors.joining(" "));
        String heard = IntStream.range(0, wordCount).mapToObj(i -> i < errors ? "x" + i : "w" + i)
                .collect(Collectors.joining(" "));
        ReadAloudContent content = ReadAloudContent.measure(passage, heard);
        assertThat(content.achieved()).isEqualTo(wordCount - errors);
        return RubricScorer.score(SpeakingRubrics.of(ScoringConstants.TASK_TYPE_READ_ALOUD), new TraitScoreSet(
                Map.of("CONTENT", content.achieved(), "PRONUNCIATION", pronunciation, "ORAL_FLUENCY", fluency),
                content.wordCount())).itemScore();
    }
}
