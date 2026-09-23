package com.pte.scoring;

import com.pte.scoring.dto.response.AiEligibleAttemptView;
import com.pte.scoring.dto.response.ScoredAnswerView;
import com.pte.scoring.internal.service.ExaminerWorkQueryService;
import com.pte.scoring.internal.service.ScoringEligibilityQueryService;
import com.pte.scoring.internal.service.ScoringReviewReadQueryService;
import com.pte.scoring.internal.service.ScoredAnswerQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringServiceTest {

    @Mock
    private ScoredAnswerQueryService scoredAnswerQueryService;

    @Mock
    private ScoringEligibilityQueryService scoringEligibilityQueryService;

    @Mock
    private ExaminerWorkQueryService examinerWorkQueryService;

    @Mock
    private ScoringReviewReadQueryService scoringReviewReadQueryService;

    private ScoringService service;

    @BeforeEach
    void setUp() {
        service = new ScoringService(scoredAnswerQueryService, scoringEligibilityQueryService,
                examinerWorkQueryService, scoringReviewReadQueryService);
    }

    @Test
    void getScoredAnswersForAttempt_delegatesToQueryService() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoredAnswerView answer1 = new ScoredAnswerView("MC_READING_SINGLE", 85);
        ScoredAnswerView answer2 = new ScoredAnswerView("READ_ALOUD", 75);

        when(scoredAnswerQueryService.findScoredForAttempt(attemptPublicId, tenantId))
                .thenReturn(List.of(answer1, answer2));

        List<ScoredAnswerView> results = service.getScoredAnswersForAttempt(attemptPublicId, tenantId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0)).isEqualTo(answer1);
        assertThat(results.get(1)).isEqualTo(answer2);
        verify(scoredAnswerQueryService).findScoredForAttempt(attemptPublicId, tenantId);
    }

    @Test
    void getScoredAnswersForAttempt_returnsEmptyList() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(scoredAnswerQueryService.findScoredForAttempt(attemptPublicId, tenantId))
                .thenReturn(List.of());

        List<ScoredAnswerView> results = service.getScoredAnswersForAttempt(attemptPublicId, tenantId);

        assertThat(results).isEmpty();
    }

    @Test
    void getScoredAnswersForAttempt_passesArgumentsUnchanged() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(scoredAnswerQueryService.findScoredForAttempt(attemptPublicId, tenantId))
                .thenReturn(List.of());

        service.getScoredAnswersForAttempt(attemptPublicId, tenantId);

        verify(scoredAnswerQueryService).findScoredForAttempt(attemptPublicId, tenantId);
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
}
