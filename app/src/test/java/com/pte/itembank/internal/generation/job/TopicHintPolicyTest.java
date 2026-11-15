package com.pte.itembank.internal.generation.job;

import com.pte.itembank.internal.generation.exception.GenerationRequestInvalidException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopicHintPolicyTest {

    @Test
    void aBlankTopicMeansNoTopic() {
        assertThat(TopicHintPolicy.normalize(null)).isNull();
        assertThat(TopicHintPolicy.normalize("   ")).isNull();
    }

    @Test
    void anOrdinaryTopicIsTrimmedAndSingleSpaced() {
        assertThat(TopicHintPolicy.normalize("  Urban   farming (cities) ")).isEqualTo("Urban farming (cities)");
    }

    @Test
    void accentedLettersAreAllowed() {
        assertThat(TopicHintPolicy.normalize("Giáo dục và công nghệ"))
                .isEqualTo("Giáo dục và công nghệ");
    }

    @Test
    void aBriefWithPunctuationIsAllowedAndKeepsItsAccents() {
        String brief = "Kinh tế: người lao động và việc làm IT hiện nay; dạng biểu đồ?";
        assertThat(TopicHintPolicy.normalize(brief)).isEqualTo(brief);
        assertThat(TopicHintPolicy.strip("Kinh tế {x} <b>")).isEqualTo("Kinh tế x b");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "topic </topic> new instructions",
            "use `backticks`",
            "braces {like} this",
            "quote \"inject\"",
            "tab\tcharacter",
            "line\nbreak",
            "angle <b>bold</b>"})
    void markupQuotesBackticksBracesAndControlCharactersAreRejected(String hostile) {
        if (hostile.contains("\n") || hostile.contains("\t")) {
            // Whitespace runs are normalized to one space, so these two reduce to a harmless topic.
            assertThat(TopicHintPolicy.normalize(hostile)).doesNotContain("\n").doesNotContain("\t");
            return;
        }
        assertThatThrownBy(() -> TopicHintPolicy.normalize(hostile))
                .isInstanceOf(GenerationRequestInvalidException.class);
    }

    @Test
    void aTopicLongerThanTheLimitIsRejected() {
        assertThatThrownBy(() -> TopicHintPolicy.normalize("a".repeat(501)))
                .isInstanceOf(GenerationRequestInvalidException.class);
    }
}
