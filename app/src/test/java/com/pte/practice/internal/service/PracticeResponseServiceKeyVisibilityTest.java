package com.pte.practice.internal.service;

import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.enums.PracticeScoringStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionKind;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.dto.response.PracticeItemResultResponse;
import com.pte.practice.internal.repository.PracticeResponseRepository;
import com.pte.scoring.ScoringService;
import com.pte.shared.practice.PracticeResponseMediaValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Which option verdicts a student may see: always in practice, in a mock test only once it is finished. */
class PracticeResponseServiceKeyVisibilityTest {

    private static final List<PracticeItemResultResponse.OptionVerdict> VERDICTS = List.of(
            new PracticeItemResultResponse.OptionVerdict(0, true, true, "a"),
            new PracticeItemResultResponse.OptionVerdict(1, false, false, "b"));

    private final PracticeScoringStateService stateService = mock(PracticeScoringStateService.class);
    private final PracticeResponse item = new PracticeResponse();
    private PracticeResponseService service;

    @BeforeEach
    void setUp() {
        service = new PracticeResponseService(mock(PracticeResponseRepository.class), null,
                mock(PracticeAnswerValidationService.class), mock(PracticeResponseMediaValidator.class), null, null,
                mock(ScoringService.class), stateService, mock(PracticeScoringRequestFactory.class));
        when(stateService.resultOf(item)).thenReturn(new PracticeItemResultResponse(UUID.randomUUID(),
                "MC_READING_MULTIPLE", PracticeScoringStatus.SCORED, 50, null, Map.of(), null, null, null, false, null,
                null, null, null, VERDICTS));
    }

    private static PracticeSession session(PracticeSessionKind kind, PracticeSessionStatus status) {
        PracticeSession session = new PracticeSession();
        session.setKind(kind);
        session.setStatus(status);
        return session;
    }

    @Test
    void aPracticeSessionShowsTheVerdictsRightAfterTheAnswer() {
        assertThat(service.resultOf(session(PracticeSessionKind.SKILL, PracticeSessionStatus.IN_PROGRESS), item)
                .optionVerdicts()).isEqualTo(VERDICTS);
        assertThat(service.resultOf(session(PracticeSessionKind.TASK, PracticeSessionStatus.IN_PROGRESS), item)
                .optionVerdicts()).isEqualTo(VERDICTS);
    }

    @Test
    void aMockTestStillBeingSatKeepsTheAnswerKeyHidden() {
        PracticeItemResultResponse result = service.resultOf(
                session(PracticeSessionKind.MOCK_TEST, PracticeSessionStatus.IN_PROGRESS), item);

        assertThat(result.optionVerdicts()).isEmpty();
        assertThat(result.score()).isEqualTo(50);
    }

    @Test
    void aFinishedMockTestShowsTheVerdictsInItsReview() {
        assertThat(service.resultOf(session(PracticeSessionKind.MOCK_TEST, PracticeSessionStatus.COMPLETED), item)
                .optionVerdicts()).isEqualTo(VERDICTS);
    }
}
