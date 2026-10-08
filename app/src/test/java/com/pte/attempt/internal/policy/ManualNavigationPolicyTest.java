package com.pte.attempt.internal.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ManualNavigationPolicy")
class ManualNavigationPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"READING", "WRITING"})
    @DisplayName("allows READING and WRITING")
    void allowsSection_readingAndWriting_true(String section) {
        assertThat(ManualNavigationPolicy.allowsSection(section)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SPEAKING", "LISTENING", "", "reading", "Writing"})
    @DisplayName("rejects SPEAKING, LISTENING, blank and wrongly-cased values")
    void allowsSection_otherSections_false(String section) {
        assertThat(ManualNavigationPolicy.allowsSection(section)).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("rejects null")
    void allowsSection_null_false(String section) {
        assertThat(ManualNavigationPolicy.allowsSection(section)).isFalse();
    }
}
