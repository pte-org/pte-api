package com.pte.itembank.internal.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reflection-only check. Guards the 2026-09-17 decision: host loses every question-bank permission, only platform
 * touches {@link QuestionController}. Per-action roles of the status route are covered by
 * {@code QuestionStatusEndpointTest}.
 */
class QuestionControllerSecurityTest {

    @Test
    void controller_isRestrictedToPlatformRoles_atClassLevel() {
        PreAuthorize annotation = QuestionController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(
                "hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')");
    }

    @Test
    void noMethodBroadensTheClassLevelRestriction() {
        for (var method : QuestionController.class.getDeclaredMethods()) {
            assertThat(method.getAnnotation(PreAuthorize.class))
                    .as("method %s must not broaden the controller's platform-only restriction (roles per status "
                            + "action are enforced inside changeStatus)", method.getName())
                    .isNull();
        }
    }
}
