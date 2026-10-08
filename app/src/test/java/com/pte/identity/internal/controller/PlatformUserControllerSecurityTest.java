package com.pte.identity.internal.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformUserControllerSecurityTest {

    @Test
    void controller_isPlatformAdminOnly() {
        PreAuthorize annotation = PlatformUserController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('PLATFORM_ADMIN')");
    }

    @Test
    void methods_doNotWeakenClassLevelAuthorization() {
        for (var method : PlatformUserController.class.getDeclaredMethods()) {
            assertThat(method.getAnnotation(PreAuthorize.class))
                    .as("method %s must use the class-level platform restriction", method.getName())
                    .isNull();
        }
    }
}
