package com.pte.scoring.internal.service;

import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.dto.response.AiEligibleAttemptView;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.support.EvaluationFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringEligibilityQueryServiceTest {

    @Mock
    private AiEvaluationRepository answerRepository;

    private ScoringEligibilityQueryService service;

    @BeforeEach
    void setUp() {
        service = new ScoringEligibilityQueryService(answerRepository);
    }

    @Test
    void eligiblePoolUsesTheScoringMethodOfEachEvaluationAndGroupsAiAnswersByAttempt() {
        UUID session = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID aiAttempt = UUID.randomUUID();
        UUID objectiveAttempt = UUID.randomUUID();
        when(answerRepository.findBySessionPublicIdAndTenantId(session, tenant)).thenReturn(List.of(
                answer(tenant, session, "CUSTOM_SPEECH", ScoringMethod.AI_SPEECH, aiAttempt),
                answer(tenant, session, "CUSTOM_TEXT", ScoringMethod.AI_TEXT, aiAttempt),
                answer(tenant, session, "CUSTOM_OBJECTIVE", ScoringMethod.OBJECTIVE, objectiveAttempt),
                answer(tenant, session, "CUSTOM_UNSCORED", ScoringMethod.UNSCORED, UUID.randomUUID()),
                answer(tenant, session, "CUSTOM_UNRULED", null, UUID.randomUUID())));

        List<AiEligibleAttemptView> result = service.findEligibleAttempts(session, tenant, null);

        assertThat(result).containsExactly(new AiEligibleAttemptView(aiAttempt, 2));
    }

    @Test
    void emptyAttemptFilterDoesNotAccidentallyExpandToTheWholeSession() {
        assertThat(service.findEligibleAttempts(UUID.randomUUID(), UUID.randomUUID(), List.of())).isEmpty();
        verify(answerRepository, never()).findBySessionPublicIdAndTenantId(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private AiEvaluation answer(UUID tenant, UUID session, String taskType, ScoringMethod method, UUID attempt) {
        return EvaluationFixtures.ai(tenant, session, attempt, UUID.randomUUID(), taskType, method);
    }
}
