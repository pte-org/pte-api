package com.pte.itembank;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskTypeKeyNormalizerTest {

    @Test
    void normalizesUnicodeInputBeforeUppercaseAndValidation() {
        assertThat(TaskTypeKeyNormalizer.normalizeKey(" read_aloud_plus "))
                .isEqualTo("READ_ALOUD_PLUS");
        assertThat(TaskTypeKeyNormalizer.normalizeKey("ｒｅａｄ＿ａｌｏｕｄ＿ｐｌｕｓ"))
                .isEqualTo("READ_ALOUD_PLUS");
    }

    @Test
    void rejectsInternalWhitespaceAndInvalidBoundaries() {
        assertThatThrownBy(() -> TaskTypeKeyNormalizer.normalizeKey("read aloud"))
                .hasMessage("TASK_TYPE_KEY_INVALID");
        assertThatThrownBy(() -> TaskTypeKeyNormalizer.normalizeKey("A"))
                .hasMessage("TASK_TYPE_KEY_INVALID");
        assertThatThrownBy(() -> TaskTypeKeyNormalizer.normalizeKey("1_READ_ALOUD"))
                .hasMessage("TASK_TYPE_KEY_INVALID");
        assertThatThrownBy(() -> TaskTypeKeyNormalizer.normalizeKey("A".repeat(65)))
                .hasMessage("TASK_TYPE_KEY_INVALID");
    }

    @Test
    void collapsesUnicodeDisplayWhitespaceAndUsesCaseInsensitiveValue() {
        assertThat(TaskTypeKeyNormalizer.normalizeDisplayName("Read   Aloud"))
                .isEqualTo("read aloud");
        assertThat(TaskTypeKeyNormalizer.normalizeDisplayName(" Read\u00a0Aloud "))
                .isEqualTo("read aloud");
    }
}
