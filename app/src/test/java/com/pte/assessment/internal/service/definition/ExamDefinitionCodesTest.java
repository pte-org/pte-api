package com.pte.assessment.internal.service.definition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ExamDefinitionCodesTest {

    @Test
    void slugifiesTheNameIncludingVietnameseLetters() {
        assertThat(ExamDefinitionCodes.fromName("Mẫu Điểm PTE 22!", code -> false)).isEqualTo("mau-diem-pte-22");
    }

    @Test
    void addsANumericSuffixWhileTheCodeIsTaken() {
        Set<String> taken = Set.of("pte", "pte-2");
        assertThat(ExamDefinitionCodes.fromName("PTE", taken::contains)).isEqualTo("pte-3");
    }

    @Test
    void fallsBackWhenTheNameHasNoLettersOrDigits() {
        assertThat(ExamDefinitionCodes.fromName("***", code -> false)).isEqualTo("template");
    }

    @Test
    void staysWithinTheColumnLength() {
        String base = "a".repeat(64);
        String code = ExamDefinitionCodes.fromName("a".repeat(100), base::equals);
        assertThat(code).hasSizeLessThanOrEqualTo(64).endsWith("-2");
    }
}
