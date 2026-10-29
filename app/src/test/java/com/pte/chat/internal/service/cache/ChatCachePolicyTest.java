package com.pte.chat.internal.service.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A cached answer is shared with strangers, and it may repeat what the student typed. So only short questions
 * with no contact details are cached; everything else is answered normally and never stored for others.
 */
class ChatCachePolicyTest {

    private static final int LIMIT = 300;

    @ParameterizedTest
    @ValueSource(strings = {
            "How should I structure a Write Essay answer?",
            "What is the difference between 'affect' and 'effect'?",
            "Cấu trúc bài Write Essay nên như thế nào?",
            "Is 2024 or 2025 the year the test changed?",
            "Describe image: a graph shows 45 percent in 2019 and 52 percent in 2020",
    })
    void aShortPlainQuestionIsCacheable(String question) {
        assertThat(ChatCachePolicy.cacheable(question, LIMIT)).isTrue();
    }

    @Test
    void aQuestionAtTheLimitIsCacheableAndOneCharacterOverIsNot() {
        assertThat(ChatCachePolicy.cacheable("a".repeat(LIMIT), LIMIT)).isTrue();
        assertThat(ChatCachePolicy.cacheable("a".repeat(LIMIT + 1), LIMIT)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Please fix my essay, I am nguyen.van.an@gmail.com and here it is",
            "mail me at a@b.co",
            "My email is Student+pte@School-Name.edu.vn please",
    })
    void aQuestionWithAnEmailAddressIsNotCacheable(String question) {
        assertThat(ChatCachePolicy.cacheable(question, LIMIT)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Call me on 0912345678 about this",
            "My phone is +84 912 345 678",
            "Phone: (028) 3822-1234 thanks",
            "0912.345.678",
    })
    void aQuestionWithAPhoneNumberIsNotCacheable(String question) {
        assertThat(ChatCachePolicy.cacheable(question, LIMIT)).isFalse();
    }

    @Test
    void shortNumbersAndYearsAreNotMistakenForPhoneNumbers() {
        assertThat(ChatCachePolicy.cacheable("I scored 79 out of 90 in 2025, 3 or 4 times", LIMIT)).isTrue();
    }

    @Test
    void aQuestionAboutYearsAndDatesIsStillCacheable() {
        assertThat(ChatCachePolicy.cacheable("What changed in 2023-2024, and on 12-05-2024?", LIMIT)).isTrue();
    }

    @Test
    void anAtSignWithoutADomainIsNotAnEmail() {
        assertThat(ChatCachePolicy.cacheable("What does the @ symbol mean in an address?", LIMIT)).isTrue();
    }
}
