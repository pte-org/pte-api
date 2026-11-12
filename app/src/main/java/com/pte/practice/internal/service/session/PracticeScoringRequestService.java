package com.pte.practice.internal.service.session;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.config.PracticeScoringProperties;
import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.enums.PracticeResponseStatus;
import com.pte.practice.internal.domain.enums.PracticeScoringStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionKind;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.dto.response.PracticeItemResultResponse;
import com.pte.practice.internal.dto.response.mocktest.PracticeMockTestScoringResponse;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.exception.PracticeSessionNotFoundException;
import com.pte.practice.internal.repository.PracticeResponseRepository;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.practice.internal.service.PracticeEntitlementService;
import com.pte.practice.internal.service.PracticeResponseService;
import com.pte.practice.internal.service.PracticeScoringStateService;
import com.pte.scoring.PracticeScoringRoute;
import com.pte.scoring.ScoringService;
import com.pte.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI scoring on demand. A speaking or writing answer is saved unscored (NONE); the student presses "Chấm điểm" for
 * one answer of a task or skill session, or once for every answer of a finished mock test. An answer is scored once:
 * a SCORED answer is final, a FAILED one goes through the retry rules, a SCORING one just reports its state.
 */
@Service
public class PracticeScoringRequestService {

    private final PracticeSessionRepository sessionRepository;
    private final PracticeEntitlementService entitlementService;
    private final PracticeResponseService itemService;
    private final PracticeResponseRepository itemRepository;
    private final PracticeScoringStateService scoringStateService;
    private final ScoringService scoringService;
    private final PracticeScoringProperties properties;
    private final Clock clock;

    public PracticeScoringRequestService(PracticeSessionRepository sessionRepository,
            PracticeEntitlementService entitlementService, PracticeResponseService itemService,
            PracticeResponseRepository itemRepository, PracticeScoringStateService scoringStateService,
            ScoringService scoringService, PracticeScoringProperties properties, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.entitlementService = entitlementService;
        this.itemService = itemService;
        this.itemRepository = itemRepository;
        this.scoringStateService = scoringStateService;
        this.scoringService = scoringService;
        this.properties = properties;
        this.clock = clock;
    }

    /** "Chấm điểm" on one answer of a task or skill session. */
    @Transactional
    public PracticeItemResultResponse requestItem(UUID sessionPublicId, UUID itemPublicId, CurrentUser caller) {
        PracticeSession session = ownedForUpdate(sessionPublicId, caller);
        entitlementService.assertUnlocked(caller, session.getTenantId());
        if (session.effectiveKind() == PracticeSessionKind.MOCK_TEST) {
            throw notAvailable();
        }
        PracticeResponse item = itemService.findForUpdate(session, itemPublicId);
        Instant now = clock.instant();
        itemService.recoverScoring(session, item, now);
        if (!isAiAnswer(item)) {
            throw notAvailable();
        }
        return switch (item.getScoringStatus()) {
            case NONE -> {
                scoringStateService.startScoring(session, item, now);
                itemRepository.saveAndFlush(item);
                yield itemService.resultOf(item);
            }
            case SCORING -> itemService.resultOf(item);
            case FAILED -> itemService.retryScoring(session, item, now);
            case SCORED -> throw new PracticeSessionException(HttpStatus.CONFLICT,
                    PracticeConstants.PRACTICE_SCORING_ALREADY_REQUESTED,
                    PracticeConstants.PRACTICE_SCORING_ALREADY_REQUESTED_MESSAGE);
        };
    }

    /** "Chấm điểm" on a finished mock test: every unscored AI answer is queued; asking again queues nothing new. */
    @Transactional
    public PracticeMockTestScoringResponse requestMockTest(UUID sessionPublicId, CurrentUser caller) {
        PracticeSession session = ownedForUpdate(sessionPublicId, caller);
        entitlementService.assertUnlocked(caller, session.getTenantId());
        if (session.effectiveKind() != PracticeSessionKind.MOCK_TEST) {
            throw notAvailable();
        }
        if (session.getStatus() != PracticeSessionStatus.COMPLETED) {
            throw new PracticeSessionException(HttpStatus.CONFLICT, PracticeConstants.PRACTICE_MOCK_TEST_NOT_FINISHED,
                    PracticeConstants.PRACTICE_MOCK_TEST_NOT_FINISHED_MESSAGE);
        }
        Instant now = clock.instant();
        itemService.recoverScoring(session, now);
        List<PracticeResponse> queued = new ArrayList<>();
        int scoring = 0;
        for (PracticeResponse item : itemService.itemsFor(session)) {
            if (!isAiAnswer(item)) {
                continue;
            }
            if (item.getScoringStatus() == PracticeScoringStatus.NONE) {
                // Jobs run a few at a time; each waits for the turn its place in the queue gives it.
                long turn = queued.size() / properties.getConcurrency();
                scoringStateService.startQueuedScoring(session, item, now,
                        Duration.ofSeconds(turn * PracticeConstants.PRACTICE_SCORING_QUEUE_SLOT_SECONDS));
                queued.add(item);
                scoring++;
            } else if (item.getScoringStatus() == PracticeScoringStatus.SCORING) {
                scoring++;
            }
        }
        itemRepository.saveAllAndFlush(queued);
        return new PracticeMockTestScoringResponse(queued.size(), scoring);
    }

    /** The caller's own session, locked: the per-session ordering every practice command follows. */
    private PracticeSession ownedForUpdate(UUID sessionPublicId, CurrentUser caller) {
        return sessionRepository.findWithLockByPublicIdAndStudentPublicId(sessionPublicId, caller.userId())
                .orElseThrow(PracticeSessionNotFoundException::new);
    }

    /** A submitted answer of a task type the AI scores (speaking or writing). */
    private boolean isAiAnswer(PracticeResponse item) {
        if (item.getStatus() != PracticeResponseStatus.ANSWERED) {
            return false;
        }
        PracticeScoringRoute route = scoringService.practiceScoringRoute(item.getTaskCode());
        return route == PracticeScoringRoute.AI_SPEECH || route == PracticeScoringRoute.AI_TEXT;
    }

    private static PracticeSessionException notAvailable() {
        return new PracticeSessionException(HttpStatus.CONFLICT, PracticeConstants.PRACTICE_SCORING_NOT_AVAILABLE,
                PracticeConstants.PRACTICE_SCORING_NOT_AVAILABLE_MESSAGE);
    }
}
