package com.pte.scoretemplate.internal.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the author-submit/admin-approval permission matrix without a Spring context. */
class ScoreTemplateControllerSecurityTest {

    @Test
    void controller_doesNotUseAClassWidePermissionThatWouldHideTheWorkflow() {
        assertThat(ScoreTemplateController.class.getAnnotation(PreAuthorize.class)).isNull();
    }

    @Test
    void draftAndFeasibilityEndpointsAllowAuthors_butActivationRequiresAdmin() {
        String authorOrAdmin = "hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')";
        for (String method : new String[] {"list", "create", "get", "clone", "replaceItems", "delete",
                "submitApproval", "feasibility"}) {
            assertRole(method, authorOrAdmin);
        }
        for (String method : new String[] {"activate", "approve", "reject"}) {
            assertRole(method, "hasRole('PLATFORM_ADMIN')");
        }
        assertRole("activeForHost", "hasRole('HOST_ADMIN')");
    }

    private void assertRole(String methodName, String expected) {
        var method = java.util.Arrays.stream(ScoreTemplateController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).as("method %s must declare its role", methodName).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
