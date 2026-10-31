package com.pte.scoring;

import com.pte.scoring.dto.response.AiEligibleAttemptView;
import com.pte.scoring.internal.service.examiner.ExaminerWorkQueryService;
import com.pte.scoring.internal.service.ai.PracticeAiScoringService;
import com.pte.scoring.internal.service.selection.ScorePublicationLockService;
import com.pte.scoring.internal.service.selection.ScoreSourceSelectionService;
import com.pte.scoring.internal.service.staff.ScoringEligibilityQueryService;
import com.pte.scoring.internal.service.review.ScoringReviewReadQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringServiceTest {

    @Mock
    private ScoringEligibilityQueryService scoringEligibilityQueryService;

    @Mock
    private ExaminerWorkQueryService examinerWorkQueryService;

    @Mock
    private ScoringReviewReadQueryService scoringReviewReadQueryService;

    @Mock
    private ScoreSourceSelectionService scoreSourceSelectionService;

    @Mock
    private ScorePublicationLockService scorePublicationLockService;

    private ScoringService service;

    @BeforeEach
    void setUp() {
        service = new ScoringService(scoringEligibilityQueryService, examinerWorkQueryService, scoringReviewReadQueryService, scoreSourceSelectionService, scorePublicationLockService, null, null);
    }

    @Test
    void findAiEligibleAttempts_delegatesPinnedEligibilityQuery() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        List<UUID> attemptIds = List.of(UUID.randomUUID());
        List<AiEligibleAttemptView> expected = List.of(new AiEligibleAttemptView(attemptIds.getFirst(), 4));
        when(scoringEligibilityQueryService.findEligibleAttempts(sessionPublicId, tenantId, attemptIds))
                .thenReturn(expected);

        assertThat(service.findAiEligibleAttempts(sessionPublicId, tenantId, attemptIds)).isEqualTo(expected);
        verify(scoringEligibilityQueryService).findEligibleAttempts(sessionPublicId, tenantId, attemptIds);
    }

    @Test
    void scorePracticeAnswer_delegatesToPracticeScoring() {
        PracticeAiScoringService practice = mock(PracticeAiScoringService.class);
        ScoringService withPractice = serviceWith(practice);
        PracticeScoringRequest request = new PracticeScoringRequest("WRITE_ESSAY", "text", null, null, null, null,
                200, 300, null, UUID.randomUUID());
        PracticeAiEvaluation evaluation = new PracticeAiEvaluation(70, Map.of(), "UNVALIDATED", "gemini", "m", "v",
                null, null, Instant.now(), "hash", "ok");
        when(practice.score(request)).thenReturn(evaluation);

        assertThat(withPractice.scorePracticeAnswer(request)).isSameAs(evaluation);
    }

    @Test
    void scorePracticeAnswer_withoutPracticeScoring_failsAsInternal() {
        PracticeScoringRequest request = new PracticeScoringRequest("WRITE_ESSAY", "text", null, null, null, null,
                null, null, null, null);

        assertThatThrownBy(() -> service.scorePracticeAnswer(request))
                .isInstanceOf(PracticeScoringException.class)
                .extracting(thrown -> ((PracticeScoringException) thrown).failure())
                .isEqualTo(PracticeScoringFailure.INTERNAL);
    }

    @Test
    void practiceScoringRoute_delegatesAndDefaultsToUnsupported() {
        PracticeAiScoringService practice = mock(PracticeAiScoringService.class);
        when(practice.routeFor("READ_ALOUD")).thenReturn(PracticeScoringRoute.AI_SPEECH);

        assertThat(serviceWith(practice).practiceScoringRoute("READ_ALOUD")).isEqualTo(PracticeScoringRoute.AI_SPEECH);
        assertThat(service.practiceScoringRoute("READ_ALOUD")).isEqualTo(PracticeScoringRoute.UNSUPPORTED);
    }

    private ScoringService serviceWith(PracticeAiScoringService practice) {
        return new ScoringService(scoringEligibilityQueryService,
                examinerWorkQueryService, scoringReviewReadQueryService, scoreSourceSelectionService,
                scorePublicationLockService, null, practice);
    }
}
