package com.pte.itembank.internal.generation.job;

import com.pte.assessment.internal.constant.GenerationPresetConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The presets read (assessment) and generation (itembank) must admit exactly the same author roles. */
class GenerationPresetRolesTest {

    @Test
    void presetRolesEqualGenerationAuthorRoles() {
        assertThat(GenerationPresetConstants.GENERATION_PRESET_ROLES)
                .containsExactlyInAnyOrderElementsOf(GenerationAccessPolicy.AUTHOR_ROLES);
        assertThat(GenerationPresetConstants.GENERATION_PRESET_AUTHORITY)
                .isEqualTo("hasAnyRole('" + String.join("','", GenerationAccessPolicy.AUTHOR_ROLES) + "')");
    }
}
