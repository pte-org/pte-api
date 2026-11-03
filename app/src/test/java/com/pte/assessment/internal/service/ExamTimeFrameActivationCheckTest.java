package com.pte.assessment.internal.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.dto.response.ExamTimeFrameResponse;
import com.pte.assessment.internal.exception.ExamDefinitionValidationException;
import com.pte.assessment.internal.service.timing.ExamTimeFrameActivationCheck;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExamTimeFrameActivationCheckTest {

    private static ExamDefinitionResponse template(ExamTimeFrameResponse frame, int listeningMax) {
        return new ExamDefinitionResponse(UUID.randomUUID(), "STANDARD", 1, "T", "DRAFT", null, List.of(
                item("READ_ALOUD", "SPEAKING", 1, 2, 100),
                item("MC_READING_SINGLE", "READING", 2, 6, 90),
                item("MC_LISTENING_SINGLE", "LISTENING", 2, listeningMax, 90)),
                "STANDARD_PTE", null, null, null, null, null, null, frame);
    }

    private static ExamDefinitionItemResponse item(String key, String section, int min, int max, int response) {
        return new ExamDefinitionItemResponse(key, section, 1, min, max, 0, response, "OBJECTIVE", BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void aTemplateWithoutATimeFrameIsNotChecked() {
        assertThatCode(() -> ExamTimeFrameActivationCheck.verify(template(null, 6), Map.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void passesWhenEveryPartCanReachItsWindow() {
        ExamTimeFrameResponse frame = new ExamTimeFrameResponse(100, 200, 300, 450, 300, 450, 5000);

        assertThatCode(() -> ExamTimeFrameActivationCheck.verify(template(frame, 6), Map.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesAndNamesThePartThatCannotReachItsWindow() {
        // Listening plans 180..180 s with at most 2 questions, so a 300..450 s window is out of reach.
        ExamTimeFrameResponse frame = new ExamTimeFrameResponse(100, 200, 300, 450, 300, 450, 5000);

        assertThatThrownBy(() -> ExamTimeFrameActivationCheck.verify(template(frame, 2), Map.of()))
                .isInstanceOf(ExamDefinitionValidationException.class)
                .hasMessageContaining("LISTENING")
                .hasMessageNotContaining("READING");
    }

    @Test
    void addsTheStimulusPlayedBeforeTheTimerWhenCheckingAPart() {
        // Speaking plans 100..200 s alone; with a 120 s stimulus per question it plans 220..440 s.
        ExamTimeFrameResponse frame = new ExamTimeFrameResponse(400, 440, 300, 450, 300, 450, 5000);

        assertThatThrownBy(() -> ExamTimeFrameActivationCheck.verify(template(frame, 6), Map.of()))
                .isInstanceOf(ExamDefinitionValidationException.class);
        assertThatCode(() -> ExamTimeFrameActivationCheck.verify(template(frame, 6), Map.of("READ_ALOUD", 120)))
                .doesNotThrowAnyException();
    }
}
