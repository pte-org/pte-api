package com.pte.scoring.internal.rubric.service;

import com.pte.assessment.ExamDefinitionService;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.rubric.dto.RubricSimulationRequest;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.rubric.RubricScoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RubricSimulationServiceTest {
    private final ScoringRubricVersionRepository versions = mock(ScoringRubricVersionRepository.class);
    private final ExamDefinitionService exams = mock(ExamDefinitionService.class);
    private final RubricSimulationService service = new RubricSimulationService(versions,
            new RubricResolver(versions, CATALOG, CODEC, exams));

    @Test
    void contentGateAndTraceComeFromTheScorerWithoutWritingOrReadingExamData() {
        var active = version(RubricVersionStatus.ACTIVE, seedJson());
        when(versions.findByStatus(RubricVersionStatus.ACTIVE)).thenReturn(Optional.of(active));
        var result = service.simulate(new RubricSimulationRequest("DESCRIBE_IMAGE", null,
                Map.of("CONTENT", BigDecimal.ZERO, "PRONUNCIATION", BigDecimal.valueOf(5),
                        "ORAL_FLUENCY", BigDecimal.valueOf(5)), null, null));
        assertThat(result.rawScore()).isZero();
        assertThat(result.scoreTrace().gateTriggeredBy()).isEqualTo("CONTENT");
        assertThat(result.scoreTrace().contributions()).hasSize(3);
        verify(versions).findByStatus(RubricVersionStatus.ACTIVE);
        verifyNoMoreInteractions(versions);
        verifyNoInteractions(exams);
    }

    @Test
    void fractionalScoresAreRejectedInsteadOfBeingCoerced() {
        var active = version(RubricVersionStatus.ACTIVE, seedJson());
        when(versions.findByStatus(RubricVersionStatus.ACTIVE)).thenReturn(Optional.of(active));
        assertThatThrownBy(() -> service.simulate(new RubricSimulationRequest("DESCRIBE_IMAGE", null,
                Map.of("CONTENT", new BigDecimal("3.5")), null, null)))
                .isInstanceOf(RubricScoreException.class).hasMessage("RUBRIC_TRAIT_OUT_OF_RANGE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "UNKNOWN", "OUT_OF_RANGE", "TOO_MANY", "TOO_LARGE", "FORM", "RA_ZERO", "RA_MISSING"})
    void invalidInputFailsWithADomainCode(String scenario) {
        var active = version(RubricVersionStatus.ACTIVE, seedJson());
        when(versions.findByStatus(RubricVersionStatus.ACTIVE)).thenReturn(Optional.of(active));
        var scores = new java.util.LinkedHashMap<>(Map.of("CONTENT", BigDecimal.ONE,
                "PRONUNCIATION", BigDecimal.ONE, "ORAL_FLUENCY", BigDecimal.ONE));
        String task = "DESCRIBE_IMAGE";
        Integer words = null;
        String code;
        switch (scenario) {
            case "MISSING" -> { scores.remove("CONTENT"); code = "RUBRIC_TRAIT_MISSING"; }
            case "UNKNOWN" -> { scores.put("UNKNOWN", BigDecimal.ONE); code = "RUBRIC_TRAIT_UNKNOWN"; }
            case "OUT_OF_RANGE" -> { scores.put("CONTENT", BigDecimal.valueOf(7)); code = "RUBRIC_TRAIT_OUT_OF_RANGE"; }
            case "TOO_MANY" -> {
                for (int i = 0; i < 17; i++) scores.put("KEY" + i, BigDecimal.ONE);
                code = "RUBRIC_TRAIT_COUNT_INVALID";
            }
            case "TOO_LARGE" -> { scores.put("x".repeat(66000), BigDecimal.ONE); code = "RUBRIC_PARAMETERS_TOO_LARGE"; }
            case "FORM" -> { task = "WRITE_ESSAY"; code = "RUBRIC_TEXT_METRICS_REQUIRED"; }
            case "RA_ZERO" -> { task = "READ_ALOUD"; words = 0; code = "RUBRIC_WORD_COUNT_INVALID"; }
            default -> { task = "READ_ALOUD"; code = "RUBRIC_WORD_COUNT_INVALID"; }
        }
        var request = new RubricSimulationRequest(task, null, scores, null, words);
        assertThatThrownBy(() -> service.simulate(request)).isInstanceOf(RubricScoreException.class).hasMessage(code);
    }
}
