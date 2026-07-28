package com.pte.scoring.service;

import com.pte.common.security.CurrentUser;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.dto.response.ScoringAnswerPageResponse;
import com.pte.scoring.messaging.outbox.OutboxWriter;
import com.pte.scoring.repository.ScoringAnswerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScoringReviewServiceTest {

    @Test
    void listPendingScopesBySessionTenantStatusAndReturnsPageMetadata() {
        ScoringAnswerRepository repository = mock(ScoringAnswerRepository.class);
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(UUID.randomUUID());
        answer.setAttemptPublicId(UUID.randomUUID());
        answer.setTaskType("WRITE_ESSAY");
        answer.setStatus(ScoringAnswerStatus.AI_SCORED_PENDING_REVIEW);
        when(repository.findBySessionPublicIdAndTenantIdAndStatus(
                eq(sessionId), eq(tenantId), eq(ScoringAnswerStatus.AI_SCORED_PENDING_REVIEW),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(answer)));
        ScoringReviewService service = new ScoringReviewService(repository, mock(OutboxWriter.class),
                mock(AttemptCompletionService.class));

        ScoringAnswerPageResponse result = service.listPending(
                sessionId, "AI_SCORED_PENDING_REVIEW", 0, 20,
                new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_AUTHOR")));

        assertEquals(1, result.items().size());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1, result.totalElements());
        verify(repository).findBySessionPublicIdAndTenantIdAndStatus(
                eq(sessionId), eq(tenantId), eq(ScoringAnswerStatus.AI_SCORED_PENDING_REVIEW),
                org.mockito.ArgumentMatchers.any(Pageable.class));
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findBySessionPublicIdAndTenantIdAndStatus(
                eq(sessionId), eq(tenantId), eq(ScoringAnswerStatus.AI_SCORED_PENDING_REVIEW),
                pageableCaptor.capture());
        assertEquals(20, pageableCaptor.getValue().getPageSize());
        assertEquals("createdAt: ASC,id: ASC", pageableCaptor.getValue().getSort().toString());
    }

    @Test
    void listPendingRejectsAnyOtherStatus() {
        ScoringReviewService service = new ScoringReviewService(mock(ScoringAnswerRepository.class),
                mock(OutboxWriter.class), mock(AttemptCompletionService.class));
        assertThrows(RuntimeException.class, () -> service.listPending(
                UUID.randomUUID(), "SCORED", 0, 20,
                new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"))));
    }

    @Test
    void listPendingRejectsUnboundedOrPlatformQueries() {
        ScoringReviewService service = new ScoringReviewService(mock(ScoringAnswerRepository.class),
                mock(OutboxWriter.class), mock(AttemptCompletionService.class));
        CurrentUser host = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));

        assertThrows(RuntimeException.class, () -> service.listPending(
                UUID.randomUUID(), "AI_SCORED_PENDING_REVIEW", -1, 20, host));
        assertThrows(RuntimeException.class, () -> service.listPending(
                UUID.randomUUID(), "AI_SCORED_PENDING_REVIEW", 0, 101, host));
        assertThrows(RuntimeException.class, () -> service.listPending(
                UUID.randomUUID(), "AI_SCORED_PENDING_REVIEW", 0, 20,
                new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"))));
    }
}
