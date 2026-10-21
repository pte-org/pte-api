package com.pte.scoring.internal.vendor.speech;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenAlignmentTest {

    @Test
    void normalizeCasefoldsDropsPunctuationAndKeepsContractions() {
        assertThat(TokenAlignment.normalize("Don't STOP, believing!")).containsExactly("don't", "stop", "believing");
        assertThat(TokenAlignment.normalize("It’s fine.")).containsExactly("it's", "fine");
        assertThat(TokenAlignment.normalize("well-known 3.5 a_b")).containsExactly("well", "known", "3", "5", "a", "b");
        assertThat(TokenAlignment.normalize("  ... ")).isEmpty();
    }

    @Test
    void identicalTextScoresOneHundred() {
        TokenAlignment alignment = TokenAlignment.align("Many universities invest.", "many universities invest");

        assertThat(alignment.score()).isEqualTo(100.0);
        assertThat(alignment.recall()).isEqualTo(100.0);
        assertThat(alignment.precision()).isEqualTo(100.0);
        assertThat(alignment.missingTokens()).isEmpty();
        assertThat(alignment.extraTokens()).isEmpty();
    }

    @Test
    void emptyCandidateScoresZeroAndReportsEveryReferenceWordMissing() {
        TokenAlignment alignment = TokenAlignment.align("one two three", "");

        assertThat(alignment.score()).isZero();
        assertThat(alignment.precision()).isZero();
        assertThat(alignment.missingTokens()).containsExactly("one", "two", "three");
    }

    @Test
    void referenceWithoutWordsIsRejected() {
        assertThatThrownBy(() -> TokenAlignment.align("... !!", "anything"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matchingBlocksKeepReferenceAndCandidatePositions() {
        TokenAlignment alignment = TokenAlignment.align("a b c d", "a x c d");

        assertThat(alignment.blocks()).extracting(TokenAlignment.Block::referenceStart,
                        TokenAlignment.Block::candidateStart, TokenAlignment.Block::size)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(0, 0, 1),
                        org.assertj.core.groups.Tuple.tuple(2, 2, 2));
    }

    /** Every line of the fixture was produced by the POC's own {@code align_tokens}. */
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("pocCases")
    void matchesThePocForTheSameInput(PocCase poc) {
        TokenAlignment alignment = TokenAlignment.align(poc.reference(), poc.candidate());

        assertThat(alignment.score()).as("score").isEqualTo(poc.score());
        assertThat(alignment.precision()).as("precision").isEqualTo(poc.precision());
        assertThat(alignment.recall()).as("recall").isEqualTo(poc.recall());
        assertThat(alignment.missingTokens()).as("missing").isEqualTo(poc.missing());
        assertThat(alignment.extraTokens()).as("extra").isEqualTo(poc.extra());
    }

    record PocCase(String reference, String candidate, double score, double precision, double recall,
                   List<String> missing, List<String> extra) {
        @Override
        public String toString() {
            return reference + " ~ " + candidate;
        }
    }

    static Stream<PocCase> pocCases() throws IOException {
        try (InputStream in = TokenAlignmentTest.class.getResourceAsStream("/scoring/alignment-parity.txt")) {
            assertThat(in).as("alignment-parity.txt").isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return text.lines().filter(line -> !line.isBlank()).map(line -> {
                String[] cells = line.split("\\|", -1);
                return new PocCase(cells[0], cells[1], Double.parseDouble(cells[2]), Double.parseDouble(cells[3]),
                        Double.parseDouble(cells[4]), words(cells[5]), words(cells[6]));
            }).toList().stream();
        }
    }

    private static List<String> words(String cell) {
        return cell.isBlank() ? List.of() : Arrays.asList(cell.split(" "));
    }
}
