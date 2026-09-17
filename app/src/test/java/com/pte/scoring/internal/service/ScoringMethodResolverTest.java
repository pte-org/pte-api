package com.pte.scoring.internal.service;

import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.scoring.domain.enums.ScoringMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Replaces the deleted hardcoded AI/objective task-type catalogs: {@code scoringMethod}
 * per task type now comes from the pinned {@code ScoreTemplate}, not two
 * hardcoded sets (spec FR-07).
 */
@ExtendWith(MockitoExtension.class)
class ScoringMethodResolverTest {

    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    @Mock
    private ScoreTemplateService scoreTemplateService;

    private ScoringMethodResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ScoringMethodResolver(scoreTemplateService);
    }

    private ScoreTemplateItemResponse item(String taskType, String scoringMethod) {
        return new ScoreTemplateItemResponse(taskType, "SPEAKING", 0, 1, 1, 0, 0, "FIXED", scoringMethod,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void resolve_taskTypeInTemplate_returnsItsScoringMethod() {
        when(scoreTemplateService.getByPublicId(TEMPLATE_ID)).thenReturn(new ScoreTemplateResponse(
                TEMPLATE_ID, "APEUNI_V5", 1, "APEUni V5", "ACTIVE",
                List.of(item("READ_ALOUD", "AI_SPEECH"), item("MC_READING_SINGLE", "OBJECTIVE"))));

        assertThat(resolver.resolve(TEMPLATE_ID, "READ_ALOUD")).contains(ScoringMethod.AI_SPEECH);
        assertThat(resolver.resolve(TEMPLATE_ID, "MC_READING_SINGLE")).contains(ScoringMethod.OBJECTIVE);
    }

    @Test
    void resolve_taskTypeNotInTemplate_returnsEmpty() {
        when(scoreTemplateService.getByPublicId(TEMPLATE_ID)).thenReturn(new ScoreTemplateResponse(
                TEMPLATE_ID, "APEUNI_V5", 1, "APEUni V5", "ACTIVE", List.of(item("READ_ALOUD", "AI_SPEECH"))));

        assertThat(resolver.resolve(TEMPLATE_ID, "PERSONAL_INTRODUCTION")).isEqualTo(Optional.empty());
    }

    @Test
    void resolve_cachesByTemplateId_doesNotRefetchOnSecondCall() {
        when(scoreTemplateService.getByPublicId(TEMPLATE_ID)).thenReturn(new ScoreTemplateResponse(
                TEMPLATE_ID, "APEUNI_V5", 1, "APEUni V5", "ACTIVE", List.of(item("READ_ALOUD", "AI_SPEECH"))));

        resolver.resolve(TEMPLATE_ID, "READ_ALOUD");
        resolver.resolve(TEMPLATE_ID, "READ_ALOUD");

        verify(scoreTemplateService, times(1)).getByPublicId(TEMPLATE_ID);
    }

    @Test
    void resolve_unscoredScoringMethod_returnsUnscored() {
        when(scoreTemplateService.getByPublicId(TEMPLATE_ID)).thenReturn(new ScoreTemplateResponse(
                TEMPLATE_ID, "APEUNI_V5", 1, "APEUni V5", "ACTIVE", List.of(item("PERSONAL_INTRODUCTION", "UNSCORED"))));

        assertThat(resolver.resolve(TEMPLATE_ID, "PERSONAL_INTRODUCTION")).contains(ScoringMethod.UNSCORED);
    }
}
