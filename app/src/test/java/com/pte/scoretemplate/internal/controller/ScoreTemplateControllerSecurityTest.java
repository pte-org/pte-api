package com.pte.scoretemplate.internal.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This codebase has no {@code @WebMvcTest}/integration-test harness to
 * "mirror" (checked: no existing controller test anywhere) — a bare
 * reflection check on the class-level {@code @PreAuthorize} is the
 * lightweight, no-Spring-context equivalent, matching how this module's
 * other tests avoid a Spring context. Guards the FR-03 finding from
 * phase-01's red-team review: every endpoint here must stay
 * PLATFORM_ADMIN-only in Plan A, since list/get can return DRAFT/RETIRED
 * templates.
 */
class ScoreTemplateControllerSecurityTest {

    @Test
    void controller_isRestrictedToPlatformAdmin_atClassLevel() {
        PreAuthorize annotation = ScoreTemplateController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('PLATFORM_ADMIN')");
    }

    @Test
    void noMethodOverridesTheClassLevelRestrictionWithABroaderRole() {
        for (var method : ScoreTemplateController.class.getDeclaredMethods()) {
            PreAuthorize methodLevel = method.getAnnotation(PreAuthorize.class);
            assertThat(methodLevel)
                    .as("method %s must not override the controller's PLATFORM_ADMIN-only restriction", method.getName())
                    .isNull();
        }
    }
}
