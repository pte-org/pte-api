package com.pte.assessment.internal.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reflection-only check (no {@code @WebMvcTest} harness in this codebase, see
 * {@code ScoreTemplateControllerSecurityTest}). Guards Plan B Phase 5: host
 * loses manual snapshot-publish/read — the skill-based exam-generation flow
 * (Phase 2/3) never calls this controller, so it's unaffected. Must not
 * merge/deploy before Phase 7/8 remove the host UIs that still call this
 * controller (see plan.md Dependencies).
 */
class SnapshotControllerSecurityTest {

    @Test
    void controller_isRestrictedToPlatformRoles_atClassLevel() {
        PreAuthorize annotation = SnapshotController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')");
    }

    @Test
    void noMethodOverridesTheClassLevelRestrictionWithABroaderRole() {
        for (var method : SnapshotController.class.getDeclaredMethods()) {
            PreAuthorize methodLevel = method.getAnnotation(PreAuthorize.class);
            assertThat(methodLevel)
                    .as("method %s must not override the controller's platform-only restriction", method.getName())
                    .isNull();
        }
    }
}
