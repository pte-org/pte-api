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

    @Test
    void generateUnused_usesTheCleanedEmailPrefix() {
        assertThat(UsernameGenerator.generateUnused("ninh-le", "LeNinh2005@gmail.com", name -> false))
                .isEqualTo("ninh-le.leninh2005");
        assertThat(UsernameGenerator.generateUnused("acme", "le.ninh+pte@gmail.com", name -> false))
                .isEqualTo("acme.leninhpte");
        assertThat(UsernameGenerator.generateUnused("acme", "Nguy\u1ec5n.An@x.vn", name -> false))
                .isEqualTo("acme.nguyenan");
    }

    @Test
    void generateUnused_capsTheEmailPrefixLength() {
        assertThat(UsernameGenerator.generateUnused("acme", "trungtamngoaingusaigon2026@x.vn", name -> false))
                .isEqualTo("acme.trungtamngoaingusaig");
    }

    @Test
    void generateUnused_numbersTheNameWhenTheOnePrefixIsTaken() {
        Set<String> taken = Set.of("acme.an", "acme.an2");

        assertThat(UsernameGenerator.generateUnused("acme", "an@gmail.com", taken::contains))
                .isEqualTo("acme.an3");
    }

    @Test
    void generateUnused_fallsBackToRandomWithoutAUsablePrefixOrWhenEveryNumberIsTaken() {
        assertThat(UsernameGenerator.generateUnused("acme", "+++@x.vn", name -> false))
                .matches("acme\\.[a-hj-km-np-z2-9]{8}");
        assertThat(UsernameGenerator.generateUnused("acme", null, name -> false))
                .matches("acme\\.[a-hj-km-np-z2-9]{8}");
        assertThat(UsernameGenerator.generateUnused("acme", "an@x.vn", name -> name.matches("acme\\.an\\d*")))
                .matches("acme\\.[a-hj-km-np-z2-9]{8}");
    }
}
