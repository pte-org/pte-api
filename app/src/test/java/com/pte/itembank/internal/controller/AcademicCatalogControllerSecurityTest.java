package com.pte.itembank.internal.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class AcademicCatalogControllerSecurityTest {

    @Test
    void legacyAndCanonicalCatalogsExposeTheAcademicAuthoringRoles() {
        String expected = "hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')";

        assertThat(QuestionTypeController.class.getAnnotation(PreAuthorize.class).value()).isEqualTo(expected);
        assertThat(TaskTypeController.class.getAnnotation(PreAuthorize.class).value()).isEqualTo(expected);
    }

    @Test
    void canonicalApprovalIsManagerOrAdminOnly() throws NoSuchMethodException {
        PreAuthorize annotation = TaskTypeController.class
                .getDeclaredMethod("approve", java.util.UUID.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER')");
    }
}
