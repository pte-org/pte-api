package com.pte.identity.internal.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UsernameGeneratorTest {

    @Test
    void generate_isTenantCodeDotEightUnambiguousCharacters() {
        for (int i = 0; i < 200; i++) {
            assertThat(UsernameGenerator.generate("acme")).matches("acme\\.[a-hj-km-np-z2-9]{8}");
        }
    }

    @Test
    void generate_doesNotRepeatAcrossManyCalls() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            seen.add(UsernameGenerator.generate("acme"));
        }
        assertThat(seen).hasSize(1_000);
    }
}
