package com.pte.practice.internal.service.timing;

import com.pte.attempt.TaskTimingCatalog;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeTimingServiceTest {

    @Mock
    private ExamDefinitionService scoreTemplateService;
    @Mock
    private TaskTimingCatalog taskTimingCatalog;

    private PracticeTimingService service() {
        return new PracticeTimingService(scoreTemplateService, taskTimingCatalog);
    }

    private static ExamDefinitionItemResponse item(String type, int prep, int response) {
        return new ExamDefinitionItemResponse(type, "SPEAKING", 1, 1, 1, prep, response, "AI",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private void activeTemplate(ExamDefinitionItemResponse... items) {
        when(scoreTemplateService.findActive()).thenReturn(Optional.of(
                new ExamDefinitionResponse(UUID.randomUUID(), "T", 1, "T", "ACTIVE", List.of(items))));
    }

    @Test
    void activeTimingsPinsTemplatePrepResponseAndCatalogPreListen() {
        activeTemplate(item("READ_ALOUD", 40, 40));
        when(taskTimingCatalog.preListenSecondsFor("READ_ALOUD")).thenReturn(OptionalInt.of(3));

        var timing = service().activeTimings().get(PracticeTimingService.normalize("READ_ALOUD"));

        assertThat(timing.preRecordSeconds()).isEqualTo(40);
        assertThat(timing.responseSeconds()).isEqualTo(40);
        assertThat(timing.preListenSeconds()).isEqualTo(3);
    }

    @Test
    void activeTimingsLeavesPreListenNullWhenTheTypeHasNone() {
        activeTemplate(item("READ_ALOUD", 40, 40));
        when(taskTimingCatalog.preListenSecondsFor("READ_ALOUD")).thenReturn(OptionalInt.empty());

        var timing = service().activeTimings().get(PracticeTimingService.normalize("READ_ALOUD"));

        assertThat(timing.preListenSeconds()).isNull();
    }

    @Test
    void noActiveTemplateMeansNoTimingsAndTimedIsUnavailable() {
        when(scoreTemplateService.findActive()).thenReturn(Optional.empty());

        assertThat(service().activeTimings()).isEmpty();
        assertThatThrownBy(() -> service().requireTimedAvailable(List.of("READ_ALOUD")))
                .isInstanceOf(PracticeSessionException.class);
    }

    @Test
    void timedRequiresEveryTaskTypeToHaveATimingRow() {
        activeTemplate(item("READ_ALOUD", 40, 40));
        when(taskTimingCatalog.preListenSecondsFor("READ_ALOUD")).thenReturn(OptionalInt.empty());
        var timings = service().activeTimings();

        assertThat(service().covers(timings, List.of("READ_ALOUD"))).isTrue();
        assertThat(service().covers(timings, List.of("READ_ALOUD", "DESCRIBE_IMAGE"))).isFalse();
    }
}
