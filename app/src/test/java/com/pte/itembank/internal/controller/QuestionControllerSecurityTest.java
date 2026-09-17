package com.pte.itembank.internal.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reflection-only check (no {@code @WebMvcTest} harness in this codebase, see
 * {@code ScoreTemplateControllerSecurityTest}). Guards the 2026-09-17 decision:
 * host loses every question-bank permission (list/get/create included) —
 * only platform touches {@link QuestionController}.
 */
class QuestionControllerSecurityTest {

    @Test
    void controller_isRestrictedToPlatformRoles_atClassLevel() {
        PreAuthorize annotation = QuestionController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')");
    }

    @Test
    void noMethodOverridesTheClassLevelRestrictionWithABroaderRole() {
        for (var method : QuestionController.class.getDeclaredMethods()) {
            PreAuthorize methodLevel = method.getAnnotation(PreAuthorize.class);
            assertThat(methodLevel)
                    .as("method %s must not override the controller's platform-only restriction", method.getName())
                    .isNull();
        }
    }
}
