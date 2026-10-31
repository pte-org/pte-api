package com.pte.chat.internal.service.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Modifier;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** The cache key must treat "the same question" typed slightly differently as one question. */
class ChatQuestionNormalizerTest {

    @Test
    void nullBecomesTheEmptyString() {
        assertThat(ChatQuestionNormalizer.normalize(null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "''|''",
            "'   '|''",
            "Hello|hello",
            "'  Hello   World  '|hello world",
            "HOW DO I USE The Present PERFECT?|how do i use the present perfect?",
            "What's 'a' collocation?|what's 'a' collocation?"})
    void trimsCollapsesAndLowercases(String input, String expected) {
        assertThat(ChatQuestionNormalizer.normalize(input)).isEqualTo(expected);
    }

    @Test
    void anyRunOfWhitespaceBecomesOneSpace() {
        assertThat(ChatQuestionNormalizer.normalize("a\t\tb\n\nc\r\n d \u000B e\f f")).isEqualTo("a b c d e f");
    }

    @Test
    void unicodeSpacesCollapseToo() {
        // no-break space, em space, ideographic space
        assertThat(ChatQuestionNormalizer.normalize("a\u00A0\u00A0b\u2003c\u3000d")).isEqualTo("a b c d");
        assertThat(ChatQuestionNormalizer.normalize("\u00A0 a \u3000")).isEqualTo("a");
    }

    @Test
    void appliesNfkcSoLookAlikeFormsMatch() {
        assertThat(ChatQuestionNormalizer.normalize("\uFF30\uFF34\uFF25")).isEqualTo("pte");     // fullwidth PTE
        assertThat(ChatQuestionNormalizer.normalize("\uFB01nd")).isEqualTo("find");                // fi ligature
    }

    @Test
    void composedAndDecomposedLettersAreOneQuestion() {
        String composed = "caf\u00E9";
        String decomposed = "cafe\u0301";

        assertThat(ChatQuestionNormalizer.normalize(decomposed)).isEqualTo(ChatQuestionNormalizer.normalize(composed));
    }

    @Test
    void vietnameseIsLowercasedAndComposed() {
        String composed = "\u0110\u1EB6T C\u00C2U";
        String decomposed = "\u0110A\u0306\u0323T CA\u0302U";

        assertThat(ChatQuestionNormalizer.normalize(composed)).isEqualTo("\u0111\u1EB7t c\u00E2u");
        assertThat(ChatQuestionNormalizer.normalize(decomposed)).isEqualTo("\u0111\u1EB7t c\u00E2u");
    }

    @Test
    void lowercasingIgnoresTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(ChatQuestionNormalizer.normalize("TITLE INDEX")).isEqualTo("title index");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void isIdempotent() {
        String once = ChatQuestionNormalizer.normalize("  \uFF28ello\u00A0 W\u00D6RLD\t ");

        assertThat(ChatQuestionNormalizer.normalize(once)).isEqualTo(once);
    }

    @Test
    void punctuationAndDigitsAreKept() {
        assertThat(ChatQuestionNormalizer.normalize("Is \"its\" vs it's, 2 or 3?")).isEqualTo("is \"its\" vs it's, 2 or 3?");
    }

    @Test
    void isAnUninstantiableFinalUtility() {
        assertThat(Modifier.isFinal(ChatQuestionNormalizer.class.getModifiers())).isTrue();
        assertThat(ChatQuestionNormalizer.class.getDeclaredConstructors())
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()));
    }
}
