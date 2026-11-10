package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.ItembankService;
import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import com.pte.itembank.internal.generation.review.ReviewVerdict;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeneratedQuestionWriterTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ItembankService itembank = mock(ItembankService.class);
    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final GenerationJobRepository jobs = mock(GenerationJobRepository.class);
    private final GenerationJobItemRepository items = mock(GenerationJobItemRepository.class);
    private final QuestionGenerationProperties properties = new QuestionGenerationProperties();
    private final MetricsCalculator metricsCalculator = new MetricsCalculator();
    private final GeneratedQuestionWriter writer = new GeneratedQuestionWriter(itembank, questions, jobs, items,
            properties, JSON, Clock.fixed(NOW, ZoneOffset.UTC), metricsCalculator);
    private final JobClaim claim = new JobClaim(5L, UUID.randomUUID(), UUID.randomUUID(), "MC_READING_SINGLE",
            CefrLevel.B2, QuestionPool.PRACTICE, null, "worker-1");
    private final CurrentUser admin = new CurrentUser(claim.requesterId(), null, List.of("PLATFORM_ADMIN"));
    private final CreateQuestionRequest request = mock(CreateQuestionRequest.class);
    private final GeneratedItem item = new GeneratedItem(request, "The  Passage, text!", "urban farming", "stub");
    private final UUID createdId = UUID.randomUUID();
    private final Question saved = new Question();

    @BeforeEach
    void setUp() {
        saved.setPublicId(createdId);
        when(jobs.statusIfLeaseHeld(5L, "worker-1")).thenReturn(List.of(JobStatus.RUNNING));
        when(questions.findByGenerationJobPublicIdAndGenerationItemIndex(any(), any())).thenReturn(Optional.empty());
        when(questions.existsByTaskTypeKeyAndContentHashAndDeletedFalse(anyString(), anyString())).thenReturn(false);
        QuestionResponse response = mock(QuestionResponse.class);
        when(response.publicId()).thenReturn(createdId);
        when(itembank.create(request, admin)).thenReturn(response);
        when(questions.findByPublicIdForUpdate(createdId)).thenReturn(Optional.of(saved));
    }

    @Test
    void aSavedQuestionCarriesTheProvenanceHashAndSlot() throws Exception {
        assertThat(writer.save(claim, admin, 2, item, null)).isEqualTo(ItemOutcome.SAVED);

        assertThat(saved.getContentHash()).isEqualTo(ContentHasher.hash("the passage text"));
        assertThat(saved.getGenerationJobPublicId()).isEqualTo(claim.jobPublicId());
        assertThat(saved.getGenerationItemIndex()).isEqualTo(2);
        JsonNode meta = JSON.readTree(saved.getGenerationMeta());
        assertThat(meta.path("schema").asInt()).isEqualTo(1);
        assertThat(meta.path("jobPublicId").asText()).isEqualTo(claim.jobPublicId().toString());
        assertThat(meta.path("itemIndex").asInt()).isEqualTo(2);
        assertThat(meta.path("topic").asText()).isEqualTo("urban farming");
        assertThat(meta.path("source").asText()).isEqualTo("stub");
        assertThat(meta.path("model").path("generator").asText()).isEqualTo("Capstone");
        assertThat(saved.getGenerationMeta()).doesNotContain("Passage");
        verify(questions).save(saved);
        verify(items).markSaved(5L, 2, createdId, "worker-1", NOW);
        verify(jobs).refreshCounters(5L);
    }

    @Test
    void theQuestionIsCreatedThroughTheNormalItembankServiceAsTheAdmin() {
        writer.save(claim, admin, 0, item, null);

        verify(itembank).create(request, admin);
    }

    @Test
    void aLostLeaseSavesNothing() {
        when(jobs.statusIfLeaseHeld(5L, "worker-1")).thenReturn(List.of());

        assertThat(writer.save(claim, admin, 0, item, null)).isEqualTo(ItemOutcome.LEASE_LOST);
        verify(itembank, never()).create(any(), any());
        verify(items, never()).markSaved(any(), any(Integer.class), any(), any(), any());
    }

    @Test
    void aCancelRequestDiscardsTheQuestionInFlight() {
        when(jobs.statusIfLeaseHeld(5L, "worker-1")).thenReturn(List.of(JobStatus.CANCELLING));

        assertThat(writer.save(claim, admin, 0, item, null)).isEqualTo(ItemOutcome.CANCELLED);
        verify(itembank, never()).create(any(), any());
        verify(items).cancelOpen(5L, "worker-1", NOW);
    }

    @Test
    void aSlotThatAlreadyHasAQuestionIsLinkedNotCreatedAgain() {
        Question already = new Question();
        UUID existingId = UUID.randomUUID();
        already.setPublicId(existingId);
        when(questions.findByGenerationJobPublicIdAndGenerationItemIndex(claim.jobPublicId(), 1))
                .thenReturn(Optional.of(already));

        assertThat(writer.save(claim, admin, 1, item, null)).isEqualTo(ItemOutcome.SAVED);

        verify(itembank, never()).create(any(), any());
        verify(items).markSaved(5L, 1, existingId, "worker-1", NOW);
    }

    @Test
    void duplicateContentFailsTheItemWithoutCreatingAQuestion() {
        when(questions.existsByTaskTypeKeyAndContentHashAndDeletedFalse("MC_READING_SINGLE",
                ContentHasher.hash("the passage text"))).thenReturn(true);

        assertThat(writer.save(claim, admin, 0, item, null)).isEqualTo(ItemOutcome.FAILED);

        verify(itembank, never()).create(any(), any());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(items).fail(any(), any(Integer.class), code.capture(), anyString(), anyString(), any());
        assertThat(code.getValue()).isEqualTo("GENERATION_DUPLICATE_CONTENT");
        verify(jobs).refreshCounters(5L);
    }
}
