package com.pte.chat.internal.constant;

import com.pte.chat.internal.prompt.ChatPrompts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ChatLanguageConstantsTest {

    @Test
    void theLanguageCodesAreLowerCaseIsoCodes() {
        assertThat(ChatConstants.LANGUAGE_EN).isEqualTo("en");
        assertThat(ChatConstants.LANGUAGE_VI).isEqualTo("vi");
    }

    @Test
    void vietnameseRefusalIsDifferentFromTheEnglishOne() {
        assertThat(ChatPrompts.refusal(ChatConstants.LANGUAGE_VI)).isNotEqualTo(ChatPrompts.refusal(ChatConstants.LANGUAGE_EN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vi", "VI", "Vi", "vI"})
    void onlyViInAnyCaseSelectsTheVietnameseRefusal(String language) {
        assertThat(ChatPrompts.refusal(language)).isEqualTo(ChatPrompts.refusal(ChatConstants.LANGUAGE_VI));
        assertThat(ChatPrompts.refusal(language)).isNotEqualTo(ChatPrompts.refusal(ChatConstants.LANGUAGE_EN));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"en", "EN", "fr", "vie", " vi", "vi ", "v", "vi-VN"})
    void everythingElseGetsTheEnglishRefusal(String language) {
        assertThat(ChatPrompts.refusal(language)).isEqualTo(ChatPrompts.refusal(ChatConstants.LANGUAGE_EN));
    }
}
