package com.pte.attempt.internal.controller;

import com.pte.attempt.internal.dto.request.RecordSecurityViolationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AttemptControllerSecurityTest {

    @Test
    void controller_isRestrictedToStudents_atClassLevel() {
        PreAuthorize annotation = AttemptController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('STUDENT')");
    }

    @Test
    void securityViolationRoute_isAnAdditiveStudentPost() throws NoSuchMethodException {
        Method method = AttemptController.class.getDeclaredMethod("recordSecurityViolation", UUID.class,
                RecordSecurityViolationRequest.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/{publicId}/security-violations");
    }
}
