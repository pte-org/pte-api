package com.pte.scoring.internal.service;

import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.exception.InvalidScoringProfileException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        return new ScoreTemplateItemResponse(taskType, "SPEAKING", 0, 1, 1, 0, 0, scoringMethod,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private ScoreTemplateItemResponse item(String taskType, String scoringMethod,
            TaskRuntimeProfileDescriptor runtime) {
        return new ScoreTemplateItemResponse(taskType, "SPEAKING", 0, 1, 1, 0, 0, scoringMethod,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, runtime);
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

    @Test
    void resolve_legacyAlias_usesCanonicalLookupKey() {
        when(scoreTemplateService.getByPublicId(TEMPLATE_ID)).thenReturn(new ScoreTemplateResponse(
                TEMPLATE_ID, "APEUNI_V5", 1, "APEUni V5", "ACTIVE",
                List.of(item("FILL_BLANKS_READING_WRITING", "OBJECTIVE"))));

        assertThat(resolver.resolve(TEMPLATE_ID, "FILL_IN_THE_BLANKS_DROPDOWN"))
                .contains(ScoringMethod.OBJECTIVE);
    }

    @Test
    void resolve_runtimeProfile_mustMatchAllowlistedScoringContract() {
        TaskRuntimeProfileDescriptor runtime = TaskRuntimeProfileRegistry.descriptorFor("READ_ALOUD");
        when(scoreTemplateService.getByPublicId(TEMPLATE_ID)).thenReturn(new ScoreTemplateResponse(
                TEMPLATE_ID, "APEUNI_V5", 1, "APEUni V5", "ACTIVE",
                List.of(item("READ_ALOUD", "OBJECTIVE", runtime))));

        assertThatThrownBy(() -> resolver.resolve(TEMPLATE_ID, "READ_ALOUD"))
                .isInstanceOf(InvalidScoringProfileException.class)
                .hasMessage("Template scoring method disagrees with the pinned profile");
    }

    @Test
    void resolve_runtimeProfile_acceptsRetiredImmutableProfileForHistoricalTemplate() {
        TaskRuntimeProfileDescriptor active = TaskRuntimeProfileRegistry.descriptorFor("READ_ALOUD");
        TaskRuntimeProfileDescriptor retired = new TaskRuntimeProfileDescriptor(
                active.taskTypeCode(), active.profileKey(), active.profileVersion(), active.behaviorKey(),
                active.rendererKey(), active.answerSchemaVersion(), active.scoringProfileKey(),
                active.scoringProfileVersion(), active.requiredClientCapabilities(), "RETIRED");
        when(scoreTemplateService.getByPublicId(TEMPLATE_ID)).thenReturn(new ScoreTemplateResponse(
                TEMPLATE_ID, "APEUNI_V5", 1, "APEUni V5", "RETIRED",
                List.of(item("READ_ALOUD", "AI_SPEECH", retired))));

        assertThat(resolver.resolve(TEMPLATE_ID, "READ_ALOUD")).contains(ScoringMethod.AI_SPEECH);
    }

    @Test
    void resolve_customTaskKey_reusesThePinnedSemanticScoringProfile() {
        TaskRuntimeProfileDescriptor standard = TaskRuntimeProfileRegistry.descriptorFor("MC_READING_SINGLE");
        TaskRuntimeProfileDescriptor custom = new TaskRuntimeProfileDescriptor(
                "MC_READING_SINGLE_PLUS", standard.profileKey(), standard.profileVersion(),
                standard.behaviorKey(), standard.rendererKey(), standard.answerSchemaVersion(),
                standard.scoringProfileKey(), standard.scoringProfileVersion(),
                standard.requiredClientCapabilities(), standard.status(), standard.screenKey(),
                standard.contractVersion(), standard.scoringMode(), standard.minSupportedAppVersion(),
                "PTE.MC_READING_SINGLE_PLUS_AUTHORING", 1);
        when(scoreTemplateService.getByPublicId(TEMPLATE_ID)).thenReturn(new ScoreTemplateResponse(
                TEMPLATE_ID, "CUSTOM", 1, "Custom", "ACTIVE",
                List.of(item("MC_READING_SINGLE_PLUS", "OBJECTIVE", custom))));

        assertThat(resolver.resolve(TEMPLATE_ID, "MC_READING_SINGLE_PLUS"))
                .contains(ScoringMethod.OBJECTIVE);
        assertThat(resolver.resolveProfile(TEMPLATE_ID, "MC_READING_SINGLE_PLUS"))
                .contains(custom);
    }
}
