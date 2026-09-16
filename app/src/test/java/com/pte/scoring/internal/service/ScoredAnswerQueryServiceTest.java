package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.dto.response.ScoredAnswerView;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoredAnswerQueryServiceTest {

    @Mock
    private ScoringAnswerRepository scoringAnswerRepository;

    private ScoredAnswerQueryService service;

    @BeforeEach
    void setUp() {
        service = new ScoredAnswerQueryService(scoringAnswerRepository);
    }

    @Test
    void findScoredForAttempt_queriesWithSCOREDStatus() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(scoringAnswerRepository.findByAttemptPublicIdAndTenantIdAndStatus(
                attemptPublicId, tenantId, ScoringAnswerStatus.SCORED))
                .thenReturn(List.of());

        service.findScoredForAttempt(attemptPublicId, tenantId);

        verify(scoringAnswerRepository).findByAttemptPublicIdAndTenantIdAndStatus(
                attemptPublicId, tenantId, ScoringAnswerStatus.SCORED);
    }

    @Test
    void findScoredForAttempt_returnsMappedScoredAnswerViews() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoringAnswer answer1 = new ScoringAnswer();
        answer1.setTaskType("MC_READING_SINGLE");
        answer1.setRawScore(85);
        answer1.setStatus(ScoringAnswerStatus.SCORED);

        ScoringAnswer answer2 = new ScoringAnswer();
        answer2.setTaskType("READ_ALOUD");
        answer2.setRawScore(75);
        answer2.setStatus(ScoringAnswerStatus.SCORED);

        when(scoringAnswerRepository.findByAttemptPublicIdAndTenantIdAndStatus(
                attemptPublicId, tenantId, ScoringAnswerStatus.SCORED))
                .thenReturn(List.of(answer1, answer2));

        List<ScoredAnswerView> results = service.findScoredForAttempt(attemptPublicId, tenantId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).taskType()).isEqualTo("MC_READING_SINGLE");
        assertThat(results.get(0).rawScore()).isEqualTo(85);
        assertThat(results.get(1).taskType()).isEqualTo("READ_ALOUD");
        assertThat(results.get(1).rawScore()).isEqualTo(75);
    }

    @Test
    void findScoredForAttempt_returnsEmptyListWhenNoScoredAnswers() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(scoringAnswerRepository.findByAttemptPublicIdAndTenantIdAndStatus(
                attemptPublicId, tenantId, ScoringAnswerStatus.SCORED))
                .thenReturn(List.of());

        List<ScoredAnswerView> results = service.findScoredForAttempt(attemptPublicId, tenantId);

        assertThat(results).isEmpty();
    }

    @Test
    void findScoredForAttempt_mapsTaskTypeCorrectly() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoringAnswer answer = new ScoringAnswer();
        answer.setTaskType("WRITE_ESSAY");
        answer.setRawScore(92);
        answer.setStatus(ScoringAnswerStatus.SCORED);

        when(scoringAnswerRepository.findByAttemptPublicIdAndTenantIdAndStatus(
                attemptPublicId, tenantId, ScoringAnswerStatus.SCORED))
                .thenReturn(List.of(answer));

        List<ScoredAnswerView> results = service.findScoredForAttempt(attemptPublicId, tenantId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).taskType()).isEqualTo("WRITE_ESSAY");
    }

    @Test
    void findScoredForAttempt_mapsRawScoreCorrectly() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoringAnswer answer = new ScoringAnswer();
        answer.setTaskType("MC_LISTENING_SINGLE");
        answer.setRawScore(42);
        answer.setStatus(ScoringAnswerStatus.SCORED);

        when(scoringAnswerRepository.findByAttemptPublicIdAndTenantIdAndStatus(
                attemptPublicId, tenantId, ScoringAnswerStatus.SCORED))
                .thenReturn(List.of(answer));

        List<ScoredAnswerView> results = service.findScoredForAttempt(attemptPublicId, tenantId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).rawScore()).isEqualTo(42);
    }

    @Test
    void findScoredForAttempt_filtersOnlyMarkedAsScored() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // Repository should only return SCORED, not PENDING
        ScoringAnswer scoredAnswer = new ScoringAnswer();
        scoredAnswer.setTaskType("MC_READING_SINGLE");
        scoredAnswer.setRawScore(80);
        scoredAnswer.setStatus(ScoringAnswerStatus.SCORED);

        when(scoringAnswerRepository.findByAttemptPublicIdAndTenantIdAndStatus(
                attemptPublicId, tenantId, ScoringAnswerStatus.SCORED))
                .thenReturn(List.of(scoredAnswer));

        List<ScoredAnswerView> results = service.findScoredForAttempt(attemptPublicId, tenantId);

        assertThat(results).hasSize(1);
        // Verify we're filtering on SCORED status
        verify(scoringAnswerRepository).findByAttemptPublicIdAndTenantIdAndStatus(
                attemptPublicId, tenantId, ScoringAnswerStatus.SCORED);
    }

    @Test
    void findScoredForAttempt_handlesMultipleAnswersWithVaryingScores() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoringAnswer answer1 = new ScoringAnswer();
        answer1.setTaskType("MC_READING_SINGLE");
        answer1.setRawScore(0);
        answer1.setStatus(ScoringAnswerStatus.SCORED);

        ScoringAnswer answer2 = new ScoringAnswer();
        answer2.setTaskType("MC_READING_MULTIPLE");
        answer2.setRawScore(100);
        answer2.setStatus(ScoringAnswerStatus.SCORED);

        ScoringAnswer answer3 = new ScoringAnswer();
        answer3.setTaskType("MC_LISTENING_SINGLE");
        answer3.setRawScore(50);
        answer3.setStatus(ScoringAnswerStatus.SCORED);

        when(scoringAnswerRepository.findByAttemptPublicIdAndTenantIdAndStatus(
                attemptPublicId, tenantId, ScoringAnswerStatus.SCORED))
                .thenReturn(List.of(answer1, answer2, answer3));

        List<ScoredAnswerView> results = service.findScoredForAttempt(attemptPublicId, tenantId);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).rawScore()).isEqualTo(0);
        assertThat(results.get(1).rawScore()).isEqualTo(100);
        assertThat(results.get(2).rawScore()).isEqualTo(50);
    }
}
