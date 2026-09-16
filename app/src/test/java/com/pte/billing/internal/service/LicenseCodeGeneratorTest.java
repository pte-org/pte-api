package com.pte.billing.internal.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LicenseCodeGeneratorTest {

    @Test
    void generate_returnsFiveReadableGroupsAndExcludesAmbiguousCharacters() {
        String code = new LicenseCodeGenerator().generate();

        assertThat(code).matches("[A-HJ-NP-Z2-9]{4}(-[A-HJ-NP-Z2-9]{4}){4}");
        assertThat(code).doesNotContain("0", "1", "I", "O");
    }
}
