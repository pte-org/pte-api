package com.pte.scoring.internal.service.selection;

import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.internal.service.review.ScoringReviewReadQueryService;
import com.pte.scoring.dto.response.ReportPublicationScoringView;
import com.pte.scoring.internal.exception.ScoreSourceSelectionException;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.session.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Publication takes the session lock, then the answer rows, then marks the session published, in that order. */
@ExtendWith(MockitoExtension.class)
class ScorePublicationLockServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @Mock
    private AiEvaluationRepository answerRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private SessionGradingGateway gateway;
    @Mock
    private ScoringReviewReadQueryService readQueryService;

    private ScorePublicationLockService service;

    @BeforeEach
    void setUp() {
        service = new ScorePublicationLockService(answerRepository, sessionService, gateway, readQueryService, com.pte.TestClocks.fixed());
        lenient().when(readQueryService.findReportInputs(tenantId, sessionId)).thenReturn(List.of());
    }

    @Test
    void publishingLocksSessionThenAnswersThenMarksThePublication() {
        UUID proposed = UUID.randomUUID();

        ReportPublicationScoringView view = service.lockForPublication(tenantId, sessionId, proposed);

        assertThat(view.publicationPublicId()).isEqualTo(proposed);
        InOrder order = inOrder(sessionService, answerRepository, gateway);
        order.verify(sessionService).lockForScoreReviewMutation(sessionId, tenantId);
        order.verify(answerRepository).findSessionAnswersForUpdate(sessionId, tenantId);
        order.verify(gateway).lockPublication(eq(tenantId), eq(sessionId), eq(proposed), any(Instant.class));
    }

    @Test
    void publishingAgainKeepsTheExistingPublicationId() {
        UUID existing = UUID.randomUUID();
        when(gateway.publication(tenantId, sessionId)).thenReturn(
                Optional.of(new SessionGradingGateway.Publication(existing, Instant.parse("2026-10-25T08:00:00Z"))));

        ReportPublicationScoringView view = service.lockForPublication(tenantId, sessionId, UUID.randomUUID());

        assertThat(view.publicationPublicId()).isEqualTo(existing);
        InOrder order = inOrder(gateway);
        order.verify(gateway).lockPublication(eq(tenantId), eq(sessionId), eq(existing), any(Instant.class));
    }

    @Test
    void aPublishedSessionRejectsFurtherScoreWrites() {
        when(gateway.isPublished(tenantId, sessionId)).thenReturn(true);

        assertThat(service.isPublished(tenantId, sessionId)).isTrue();
        assertThatThrownBy(() -> service.assertNotPublished(tenantId, sessionId))
                .isInstanceOf(ScoreSourceSelectionException.class);
    }

    @Test
    void anUnpublishedSessionAcceptsScoreWrites() {
        service.assertNotPublished(tenantId, sessionId);

        assertThat(service.isPublished(tenantId, sessionId)).isFalse();
    }
}
