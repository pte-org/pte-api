package com.pte.assessment.internal.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

/** Host templates are tenant-owned: only a host admin may reach any of their routes. */
class HostExamTemplateControllerSecurityTest {

    @Test
    void everyRouteIsRestrictedToHostAdmins() {
        PreAuthorize annotation = HostExamTemplateController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('HOST_ADMIN')");
    }

    @Test
    void noRouteWidensTheClassWideRestriction() {
        for (var method : HostExamTemplateController.class.getDeclaredMethods()) {
            assertThat(method.getAnnotation(PreAuthorize.class))
                    .as("method %s must not override the class-wide role", method.getName()).isNull();
        }
    }
}
