package com.pte.itembank;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticVersionTest {

    @Test
    void comparesStrictMajorMinorPatchVersions() {
        assertThat(SemanticVersion.parse("1.10.0")).isGreaterThan(SemanticVersion.parse("1.9.9"));
        assertThat(SemanticVersion.parse("2.0.0").satisfiesMinimum("1.99.99")).isTrue();
    }

    @Test
    void rejectsMalformedAndPrereleaseVersions() {
        assertThatThrownBy(() -> SemanticVersion.parse("1.0"))
                .hasMessage("UNSUPPORTED_RUNTIME_CONTRACT");
        assertThatThrownBy(() -> SemanticVersion.parse("1.0.0-beta"))
                .hasMessage("UNSUPPORTED_RUNTIME_CONTRACT");
    }
}
