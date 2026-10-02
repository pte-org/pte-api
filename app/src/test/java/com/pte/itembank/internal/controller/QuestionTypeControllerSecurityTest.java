package com.pte.itembank.internal.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionTypeControllerSecurityTest {

    @Test
    void controller_isRestrictedToPlatformUsers_atClassLevel() {
        PreAuthorize annotation = QuestionTypeController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')");
    }

    @Test
    void noMethodOverridesTheClassLevelRestriction() {
        for (var method : QuestionTypeController.class.getDeclaredMethods()) {
            PreAuthorize methodLevel = method.getAnnotation(PreAuthorize.class);
            assertThat(methodLevel)
                    .as("method %s must use the class-level platform restriction", method.getName())
                    .isNull();
        }
    }
}
