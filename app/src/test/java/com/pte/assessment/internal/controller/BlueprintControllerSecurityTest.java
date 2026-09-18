package com.pte.assessment.internal.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reflection-only check (no {@code @WebMvcTest} harness in this codebase, see
 * {@code ScoreTemplateControllerSecurityTest}). Guards Plan B Phase 5: host
 * loses the manual blueprint-authoring flow — only platform still composes
 * blueprints by hand (vendor-web's own {@code /admin/exams}, out of Plan B's
 * scope). Must not merge/deploy before Phase 7/8 remove the host UIs that
 * still call this controller (see plan.md Dependencies).
 */
class BlueprintControllerSecurityTest {

    @Test
    void controller_isRestrictedToPlatformRoles_atClassLevel() {
        PreAuthorize annotation = BlueprintController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')");
    }

    @Test
    void methodLevelRestrictionsOnlyNarrowTheClassLevelRestriction() {
        for (var method : BlueprintController.class.getDeclaredMethods()) {
            PreAuthorize methodLevel = method.getAnnotation(PreAuthorize.class);
            if (method.getName().equals("approve") || method.getName().equals("reject")) {
                assertThat(methodLevel).isNotNull();
                assertThat(methodLevel.value()).isEqualTo("hasRole('PLATFORM_ADMIN')");
            } else {
                assertThat(methodLevel)
                        .as("method %s must not broaden the controller's platform-only restriction", method.getName())
                        .isNull();
            }
        }
    }
}
