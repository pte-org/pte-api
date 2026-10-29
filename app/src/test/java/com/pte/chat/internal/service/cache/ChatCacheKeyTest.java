package com.pte.chat.internal.service.cache;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

/** The key decides who shares an answer: same question and settings share, anything else must not. */
class ChatCacheKeyTest {

    private static final String QUESTION = "How do I use the present perfect?";

    private static String key(String question, String language, String taskType, String version) {
        return ChatCacheKey.of(question, language, taskType, version);
    }

    @Test
    void isLowercaseHexSha256() {
        assertThat(key(QUESTION, "en", "WRITE_ESSAY", "v1")).matches("[0-9a-f]{64}");
    }

    @Test
    void isDeterministic() {
        assertThat(key(QUESTION, "en", "WRITE_ESSAY", "v1")).isEqualTo(key(QUESTION, "en", "WRITE_ESSAY", "v1"));
    }

    @Test
    void caseAndWhitespaceDoNotChangeTheKey() {
        String base = key(QUESTION, "en", "WRITE_ESSAY", "v1");

        assertThat(key("  HOW do I   use the\tPresent PERFECT?\n", "en", "WRITE_ESSAY", "v1")).isEqualTo(base);
        assertThat(key("how do i use the present perfect?", "en", "WRITE_ESSAY", "v1")).isEqualTo(base);
    }

    @Test
    void compatibilityFormsOfTheSameTextShareAKey() {
        assertThat(key("ＰＴＥ tips", "en", null, "v1")).isEqualTo(key("PTE tips", "en", null, "v1"));
    }

    @Test
    void aDifferentQuestionGivesADifferentKey() {
        assertThat(key(QUESTION, "en", null, "v1")).isNotEqualTo(key(QUESTION + "!", "en", null, "v1"));
        assertThat(key("a b", "en", null, "v1")).isNotEqualTo(key("ab", "en", null, "v1"));
    }

    @Test
    void aDifferentLanguageGivesADifferentKey() {
        assertThat(key(QUESTION, "en", "WRITE_ESSAY", "v1")).isNotEqualTo(key(QUESTION, "vi", "WRITE_ESSAY", "v1"));
    }

    @Test
    void aDifferentTaskTypeGivesADifferentKey() {
        assertThat(key(QUESTION, "en", "WRITE_ESSAY", "v1")).isNotEqualTo(key(QUESTION, "en", "READ_ALOUD", "v1"));
        assertThat(key(QUESTION, "en", "WRITE_ESSAY", "v1")).isNotEqualTo(key(QUESTION, "en", null, "v1"));
    }

    @Test
    void aDifferentPromptVersionGivesADifferentKeySoACacheDiesWithItsPrompt() {
        assertThat(key(QUESTION, "en", "WRITE_ESSAY", "v1")).isNotEqualTo(key(QUESTION, "en", "WRITE_ESSAY", "v2"));
    }

    @Test
    void nullLanguageAndTaskTypeAreTreatedAsEmpty() {
        assertThat(key(QUESTION, null, null, "v1")).isEqualTo(key(QUESTION, "", "", "v1"));
        assertThat(key(QUESTION, null, "WRITE_ESSAY", "v1")).isEqualTo(key(QUESTION, "", "WRITE_ESSAY", "v1"));
    }

    @Test
    void aNullQuestionIsTheEmptyQuestion() {
        assertThat(key(null, "en", null, "v1")).isEqualTo(key("", "en", null, "v1"));
    }

    @Test
    void fieldsAreSeparatedUnambiguously() {
        // moving a character across a field boundary must change the key
        assertThat(key("ab", "c", "", "v1")).isNotEqualTo(key("a", "bc", "", "v1"));
        assertThat(key(QUESTION, "vi", "X", "v1")).isNotEqualTo(key(QUESTION, "viX", "", "v1"));
        assertThat(key(QUESTION, "en", "ab", "c")).isNotEqualTo(key(QUESTION, "en", "a", "bc"));
        assertThat(key(QUESTION, "", "en", "v1")).isNotEqualTo(key(QUESTION, "en", "", "v1"));
    }

    @Test
    void hugeInputStillGivesAFixedSizeKey() {
        assertThat(key("word ".repeat(10_000), "en", null, "v1")).hasSize(64);
    }

    @Test
    void isAnUninstantiableFinalUtility() {
        assertThat(Modifier.isFinal(ChatCacheKey.class.getModifiers())).isTrue();
        assertThat(ChatCacheKey.class.getDeclaredConstructors())
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()));
    }
}
