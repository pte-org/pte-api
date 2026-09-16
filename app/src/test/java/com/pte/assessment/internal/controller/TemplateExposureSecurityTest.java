package com.pte.assessment.internal.controller;

import com.pte.assessment.dto.response.SnapshotResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateExposureSecurityTest {

    @Test
    void hostCannotReachBlueprintSnapshotOrQuestionContentSurfaces() {
        assertPlatformOnly(BlueprintController.class);
        assertPlatformOnly(SnapshotController.class);
        assertPlatformOnly(com.pte.itembank.internal.controller.QuestionController.class);

        assertThat(Arrays.stream(SnapshotResponse.class.getRecordComponents())
                .map(component -> component.getName()))
                .doesNotContain("randomSeed", "promptText", "correctAnswerText", "optionsJson");
    }

    private void assertPlatformOnly(Class<?> controllerType) {
        PreAuthorize authorization = controllerType.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).contains("PLATFORM").doesNotContain("HOST");
    }
}
