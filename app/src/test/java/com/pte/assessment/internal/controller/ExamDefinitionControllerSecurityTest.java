package com.pte.assessment.internal.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the author-submit/admin-approval permission matrix without a Spring context. */
class ExamDefinitionControllerSecurityTest {

    @Test
    void controller_doesNotUseAClassWidePermissionThatWouldHideTheWorkflow() {
        assertThat(ExamDefinitionController.class.getAnnotation(PreAuthorize.class)).isNull();
    }

    @Test
    void draftAndFeasibilityEndpointsAllowAuthors() {
        String authorOrAdmin =
                "hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')";
        for (String method : new String[] {"list", "create", "get", "clone", "replaceItems", "delete",
                "changeStatus", "feasibility"}) {
            assertRole(method, authorOrAdmin);
        }
        assertRole("activeForHost", "hasRole('HOST_ADMIN')");
    }

    private void assertRole(String methodName, String expected) {
        var method = java.util.Arrays.stream(ExamDefinitionController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).as("method %s must declare its role", methodName).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
