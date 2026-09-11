package com.pte.iam.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordGeneratorTest {

    @Test
    void generateReadable_matchesFixedShapeAndUnambiguousCharset() {
        String password = PasswordGenerator.generateReadable();

        assertThat(password).matches("^[2-9A-HJ-NP-Za-hj-np-z]{4}-[2-9A-HJ-NP-Za-hj-np-z]{4}$");
        assertThat(password).doesNotContain("0", "O", "1", "l", "I");
    }

    @Test
    void generateReadable_isRandomAcrossCalls() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            generated.add(PasswordGenerator.generateReadable());
        }

        assertThat(generated).hasSize(50);
    }
}
