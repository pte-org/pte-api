package com.pte.attempt.internal.service;

import com.pte.attempt.domain.AttemptAnswer;
import com.pte.attempt.internal.repository.AttemptAnswerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The host source choice is stored on the answer itself. */
@ExtendWith(MockitoExtension.class)
class AnswerScoreSelectionServiceTest {

    @Mock
    private AttemptAnswerRepository answers;

    private AnswerScoreSelectionService service;

    @BeforeEach
    void setUp() {
        service = new AnswerScoreSelectionService(answers);
    }

    @Test
    void theChosenSourceActorTimeAndEvaluationAreWrittenOnEveryAnswer() {
        UUID tenantId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID evaluation = UUID.randomUUID();
        Instant at = Instant.parse("2026-10-24T10:00:00Z");
        AttemptAnswer first = answer();
        AttemptAnswer second = answer();
        List<UUID> ids = List.of(first.getPublicId(), second.getPublicId());
        when(answers.findAllByPublicIdInAndAttempt_TenantId(ids, tenantId)).thenReturn(List.of(first, second));

        int changed = service.select(ids, tenantId, "EXAMINER", actor, at, Map.of(first.getPublicId(), evaluation));

        assertThat(changed).isEqualTo(2);
        assertThat(first.getSelectedScoreSource()).isEqualTo("EXAMINER");
        assertThat(first.getSelectedScoreSourceByPublicId()).isEqualTo(actor);
        assertThat(first.getSelectedScoreSourceAt()).isEqualTo(at);
        assertThat(first.getSelectedEvaluationPublicId()).isEqualTo(evaluation);
        assertThat(second.getSelectedEvaluationPublicId()).isNull();
        verify(answers).saveAll(List.of(first, second));
    }

    @Test
    void nothingIsReadOrWrittenForAnEmptySelection() {
        assertThat(service.select(List.of(), UUID.randomUUID(), "AI", UUID.randomUUID(), Instant.now(), Map.of()))
                .isZero();
        verify(answers, never()).saveAll(any());
    }

    private AttemptAnswer answer() {
        AttemptAnswer answer = new AttemptAnswer();
        ReflectionTestUtils.setField(answer, "publicId", UUID.randomUUID());
        return answer;
    }
}
