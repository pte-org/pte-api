package com.pte.itembank.internal.generation.job;

import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.domain.ItemStage;
import com.pte.itembank.internal.generation.dto.AiStatusResponse;
import com.pte.itembank.internal.generation.dto.GenerationStatsResponse;
import com.pte.itembank.internal.generation.prompt.PromptTestRegistry;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAdminServicesTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    private final GenerationJobItemRepository items = mock(GenerationJobItemRepository.class);
    private final com.pte.itembank.internal.generation.usage.GenerationUsageRepository usage =
            mock(com.pte.itembank.internal.generation.usage.GenerationUsageRepository.class,
                    invocation -> com.pte.itembank.internal.generation.usage.GenerationUsage.NONE);
    private final GenerationStatsService stats =
            new GenerationStatsService(items, usage, new com.pte.itembank.internal.generation.config.QuestionGenerationProperties(),
                    Clock.fixed(NOW, ZoneOffset.UTC));

    private static Object[] row(String taskType, ItemStage stage, String code, long count) {
        return new Object[] {taskType, stage, code, count};
    }

    @Test
    void countsSavedAndFailedItemsPerTaskTypeAndPerFailureCode() {
        when(items.countOutcomesBetween(any(), any())).thenReturn(List.of(
                row("WRITE_ESSAY", ItemStage.SAVED, null, 4),
                row("WRITE_ESSAY", ItemStage.FAILED, "GENERATION_REVIEW_FAILED", 2),
                row("READ_ALOUD", ItemStage.SAVED, null, 1),
                row("READ_ALOUD", ItemStage.FAILED, "GENERATION_PROVIDER_UNAVAILABLE", 5),
                row("READ_ALOUD", ItemStage.FAILED, null, 1)));

        GenerationStatsResponse response = stats.stats(null);

        assertThat(response.saved()).isEqualTo(5);
        assertThat(response.failed()).isEqualTo(8);
        assertThat(response.reviewRejected()).isEqualTo(2);
        assertThat(response.byTaskType()).extracting(GenerationStatsResponse.TaskTypeStats::taskType)
                .containsExactly("READ_ALOUD", "WRITE_ESSAY");
        assertThat(response.byTaskType().get(1).saved()).isEqualTo(4);
        assertThat(response.byTaskType().get(1).failed()).isEqualTo(2);
        // The most frequent failure first; a failure with no code is reported as an internal error.
        assertThat(response.failures().get(0).code()).isEqualTo("GENERATION_PROVIDER_UNAVAILABLE");
        assertThat(response.failures()).extracting(GenerationStatsResponse.FailureStats::code)
                .contains("GENERATION_INTERNAL_ERROR");
    }

    @Test
    void theWindowDefaultsToThirtyDaysAndIsClamped() {
        when(items.countOutcomesBetween(any(), any())).thenReturn(List.of());

        assertThat(stats.stats(null).days()).isEqualTo(30);
        assertThat(stats.stats(0).days()).isEqualTo(1);
        assertThat(stats.stats(100000).days()).isEqualTo(365);

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(items, org.mockito.Mockito.atLeastOnce()).countOutcomesBetween(since.capture(), any());
        assertThat(since.getAllValues().get(0)).isEqualTo(NOW.minus(Duration.ofDays(30)));
    }

    @Test
    void aPickedRangeCoversWholeVietnamDaysBothEndsIncluded() {
        when(items.countOutcomesBetween(any(), any())).thenReturn(List.of());
        when(usage.sum(java.time.LocalDate.of(2026, 11, 2), java.time.LocalDate.of(2026, 11, 8)))
                .thenReturn(new com.pte.itembank.internal.generation.usage.GenerationUsage(4, 1200, 300));
        assertThat(stats.stats(java.time.LocalDate.of(2026, 11, 2), java.time.LocalDate.of(2026, 11, 8)).tokens())
                .isEqualTo(new GenerationStatsResponse.TokenStats(4, 1200, 300, new java.math.BigDecimal("0.000240")));
        org.mockito.Mockito.clearInvocations(items);

        assertThat(stats.stats(java.time.LocalDate.of(2026, 11, 8), java.time.LocalDate.of(2026, 11, 2)).days())
                .isEqualTo(7);

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> until = ArgumentCaptor.forClass(Instant.class);
        verify(items).countOutcomesBetween(since.capture(), until.capture());
        assertThat(since.getValue()).isEqualTo(Instant.parse("2026-11-01T17:00:00Z"));
        assertThat(until.getValue()).isEqualTo(Instant.parse("2026-11-08T17:00:00Z"));
        assertThat(stats.stats(java.time.LocalDate.of(2020, 1, 1), java.time.LocalDate.of(2026, 11, 8)).days())
                .isEqualTo(365);
    }

    @Test
    void anEmptyWindowGivesZeros() {
        when(items.countOutcomesBetween(any(), any())).thenReturn(List.of());

        GenerationStatsResponse response = stats.stats(7);

        assertThat(response.saved()).isZero();
        assertThat(response.byTaskType()).isEmpty();
        assertThat(response.failures()).isEmpty();
    }

    private AiOverviewService overview(QuestionGenerationProperties properties) {
        GenerationTaskTypeCatalog catalog = mock(GenerationTaskTypeCatalog.class);
        when(catalog.supported()).thenReturn(List.of());
        return new AiOverviewService(properties, PromptTestRegistry.create(), catalog);
    }

    @Test
    void theStatusReportsWhetherKeysExistAndOnlyTheHostOfEachProvider() {
        QuestionGenerationProperties properties = new QuestionGenerationProperties();
        properties.setEnabled(true);
        properties.getLlm().setBaseUrl("https://router.example.test/v1");
        properties.getLlm().setApiKey("sk-secret-value");
        properties.getKokoro().setBaseUrl("https://tts.example.test/kokoro");

        AiStatusResponse status = overview(properties).status();

        assertThat(status.generationEnabled()).isTrue();
        assertThat(status.providerHost()).isEqualTo("router.example.test");
        assertThat(status.providerKeyConfigured()).isTrue();
        assertThat(status.speechHost()).isEqualTo("tts.example.test");
        assertThat(status.speechKeyConfigured()).isFalse();
        assertThat(status.generatorModel()).isEqualTo("Capstone");
        // Nothing in the response may carry the key or a path of the provider URL.
        assertThat(status.toString()).doesNotContain("sk-secret-value").doesNotContain("/v1").doesNotContain("kokoro");
    }

    @Test
    void anUnsetOrInvalidProviderUrlHasNoHost() {
        QuestionGenerationProperties properties = new QuestionGenerationProperties();
        properties.getLlm().setBaseUrl("not a url");

        AiStatusResponse status = overview(properties).status();

        assertThat(status.providerHost()).isEmpty();
        assertThat(status.speechHost()).isEmpty();
        assertThat(status.providerKeyConfigured()).isFalse();
    }
}
