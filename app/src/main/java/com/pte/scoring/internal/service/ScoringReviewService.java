package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptOwnerView;
import com.pte.media.MediaService;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.dto.response.AnswerListItemResponse;
import com.pte.scoring.internal.dto.response.AnswerListResponse;
import com.pte.scoring.internal.dto.response.AnswerPayloadKind;
import com.pte.scoring.internal.dto.response.AnswerReviewDetailResponse;
import com.pte.scoring.internal.dto.response.DecodedAnswerPayload;
import com.pte.scoring.internal.dto.response.ScoringAnswerResponse;
import com.pte.scoring.internal.exception.AnswerNotFoundException;
import com.pte.scoring.internal.exception.InvalidAnswerStatusException;
import com.pte.scoring.internal.exception.ScoreSourceSelectionException;
import com.pte.scoring.internal.constant.ScoreReviewConstants;
import com.pte.scoring.internal.mapper.ScoringAnswerMapper;
import com.pte.scoring.internal.repository.AiDetailFlags;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
import com.pte.shared.security.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Host-facing answer review: list, view full decoded content + presigned
 * media, and record an independent teacher score. Tenant-scoped throughout;
 * denial is 404, not 403 (no existence leak, same pattern as {@code
 * enrollment}'s host-facing services).
 *
 * <p>No approval gate — an AI score always finalizes on its own, same as
 * every other task type; a host's own score is independent data for future
 * AI-vs-teacher comparison stats, never a gate.
 */
@Service
public class ScoringReviewService {

    private static final Logger log = LoggerFactory.getLogger(ScoringReviewService.class);

    /** Fixed TTL for a review-screen playback link — no exam session window to derive it from here, just a human clicking play. */
    private static final long MEDIA_URL_TTL_SECONDS = 3600;

    private final ScoringAnswerRepository scoringAnswerRepository;
    private final AnswerPayloadDecoder answerPayloadDecoder;
    private final MediaService mediaService;
    private final ScoringSessionStateRepository sessionStateRepository;
    private final EntityManager entityManager;
    private final AttemptService attemptService;
    private final AiScoreDetailQueryService aiDetails;
    private final ScoringEligibilityQueryService eligibility;
    private final com.pte.session.SessionService sessionService;
    private final com.pte.scoring.internal.repository.GradingCohortRepository cohortRepository;

    @Autowired
    public ScoringReviewService(ScoringAnswerRepository scoringAnswerRepository,
            AnswerPayloadDecoder answerPayloadDecoder, MediaService mediaService,
            ScoringSessionStateRepository sessionStateRepository, EntityManager entityManager,
            AttemptService attemptService, AiScoreDetailQueryService aiDetails,
            ScoringEligibilityQueryService eligibility, com.pte.session.SessionService sessionService,
            com.pte.scoring.internal.repository.GradingCohortRepository cohortRepository) {
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.answerPayloadDecoder = answerPayloadDecoder;
        this.mediaService = mediaService;
        this.sessionStateRepository = sessionStateRepository;
        this.entityManager = entityManager;
        this.attemptService = attemptService;
        this.aiDetails = aiDetails;
        this.eligibility = eligibility;
        this.sessionService = sessionService;
        this.cohortRepository = cohortRepository;
    }

    public ScoringReviewService(ScoringAnswerRepository scoringAnswerRepository,
            AnswerPayloadDecoder answerPayloadDecoder, MediaService mediaService,
            ScoringSessionStateRepository sessionStateRepository, EntityManager entityManager,
            AttemptService attemptService, AiScoreDetailQueryService aiDetails,
            ScoringEligibilityQueryService eligibility) {
        this(scoringAnswerRepository, answerPayloadDecoder, mediaService, sessionStateRepository, entityManager,
                attemptService, aiDetails, eligibility, null, null);
    }

    public ScoringReviewService(ScoringAnswerRepository scoringAnswerRepository,
            AnswerPayloadDecoder answerPayloadDecoder, MediaService mediaService,
            ScoringSessionStateRepository sessionStateRepository, EntityManager entityManager,
            AttemptService attemptService) {
        this(scoringAnswerRepository, answerPayloadDecoder, mediaService, sessionStateRepository, entityManager,
                attemptService, null, null);
    }

    public ScoringReviewService(ScoringAnswerRepository scoringAnswerRepository,
            AnswerPayloadDecoder answerPayloadDecoder, MediaService mediaService,
            ScoringSessionStateRepository sessionStateRepository, EntityManager entityManager) {
        this(scoringAnswerRepository, answerPayloadDecoder, mediaService, sessionStateRepository, entityManager,
                null, null, null);
    }

    public ScoringReviewService(ScoringAnswerRepository scoringAnswerRepository,
            AnswerPayloadDecoder answerPayloadDecoder, MediaService mediaService) {
        this(scoringAnswerRepository, answerPayloadDecoder, mediaService, null, null, null, null, null);
    }

    /**
     * Tenant-scoped answer review list — a host is always tenant-scoped
     * ({@link com.pte.scoring.internal.controller.ScoringReviewController}
     * grants only HOST_ADMIN, never a platform role), so tenantId
     * always comes from {@code caller}, never a request parameter.
     */
    @Transactional(readOnly = true)
    public AnswerListResponse listAnswers(UUID sessionPublicId, UUID attemptPublicId, String statusFilter,
                                          Pageable pageable, CurrentUser caller) {
        return listAnswers(sessionPublicId, attemptPublicId, statusFilter, null, pageable, caller);
    }

    @Transactional(readOnly = true)
    public AnswerListResponse listAnswers(UUID sessionPublicId, UUID attemptPublicId, String statusFilter,
            String search, Pageable pageable, CurrentUser caller) {
        ScoringAnswerStatus status = parseStatus(statusFilter);
        String normalizedSearch = search == null ? null : search.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalizedSearch != null && normalizedSearch.isBlank()) {
            normalizedSearch = null;
        }
        Page<ScoringAnswer> page = normalizedSearch == null
                ? scoringAnswerRepository.findForReview(caller.tenantId(), sessionPublicId, attemptPublicId, status,
                        pageable)
                : scoringAnswerRepository.findForReviewWithSearch(caller.tenantId(), sessionPublicId,
                        attemptPublicId, status, normalizedSearch, parseSearchStatus(normalizedSearch),
                        parseUuid(normalizedSearch), pageable);
        log.debug("Host {} listed answers (tenantId={}, sessionPublicId={}, status={}) -> {} results",
                caller.userId(), caller.tenantId(), sessionPublicId, status, page.getNumberOfElements());
        Map<UUID, AttemptOwnerView> attemptOwners = attemptService == null ? Map.of()
                : attemptService.getAttemptOwners(sessionPublicId, caller.tenantId(), page.getContent().stream()
                        .map(ScoringAnswer::getAttemptPublicId).distinct().toList());
        Map<UUID, AiDetailFlags> aiFlags = aiDetails == null ? Map.of()
                : aiDetails.findFlags(caller.tenantId(), page.getContent().stream()
                        .map(ScoringAnswer::getAnswerPublicId).toList());
        List<AnswerListItemResponse> items = page.getContent().stream()
                .map(answer -> new AnswerListItemResponse(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                        answer.getSessionPublicId(), answer.getTaskType(), answer.getStatus().name(),
                        answer.getRawScore(), answer.getTeacherScore(), answer.getCreatedAt(),
                        attemptNumber(attemptOwners.get(answer.getAttemptPublicId())),
                        isFallback(aiFlags.get(answer.getAnswerPublicId())),
                        eligibility != null && eligibility.isAiEligible(answer)))
                .toList();
        return new AnswerListResponse(items, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }

    /**
     * Full review detail for one answer — tenant isolation reuses {@link
     * #findOwned}. Media presign failure never fails the whole endpoint: the
     * answer content is still visible, just without a playback link.
     */
    @Transactional(readOnly = true)
    public AnswerReviewDetailResponse getAnswerForReview(UUID answerPublicId, CurrentUser caller) {
        ScoringAnswer answer = findOwned(answerPublicId, caller);
        DecodedAnswerPayload decoded = answerPayloadDecoder.decode(answer);
        if (decoded.kind() == AnswerPayloadKind.AUDIO && decoded.mediaPublicId() != null) {
            decoded = withPresignedUrl(decoded, answer.getTenantId());
        }
        return new AnswerReviewDetailResponse(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getSessionPublicId(), answer.getTaskType(), answer.getStatus().name(), answer.getRawScore(),
                answer.getTeacherScore(), answer.getCreatedAt(), decoded,
                aiDetails == null ? null : aiDetails.find(answer));
    }

    /**
     * Records a host's own independent score — never gated by {@code
     * status}, never touches {@code rawScore}. Purely parallel data for a
     * future AI-vs-teacher comparison feature; which score is "official" for
     * student-facing reports is undecided (out of scope here).
     */
    @Transactional
    public ScoringAnswerResponse submitTeacherScore(UUID answerPublicId, int score, CurrentUser caller) {
        ScoringAnswer answer = findOwned(answerPublicId, caller);
        if (sessionService != null) {
            sessionService.lockForScoreReviewMutation(answer.getSessionPublicId(), caller.tenantId());
        }
        if (cohortRepository != null) {
            cohortRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(caller.tenantId(), answer.getSessionPublicId())
                    .ifPresent(cohort -> {
                        if (cohort.getMarkingMode() == com.pte.scoring.domain.enums.GradingMarkingMode.MANUAL_EXAMINER) {
                            throw new ScoreSourceSelectionException(HttpStatus.CONFLICT,
                                    ScoreReviewConstants.MANUAL_EXAMINER_SELECTION_PROHIBITED, null,
                                    ScoreReviewConstants.MANUAL_EXAMINER_SELECTION_PROHIBITED_MESSAGE);
                        }
                    });
        }
        if (entityManager != null) {
            entityManager.lock(answer, LockModeType.PESSIMISTIC_WRITE);
            entityManager.refresh(answer, LockModeType.PESSIMISTIC_WRITE);
            sessionStateRepository.findByTenantIdAndSessionPublicId(caller.tenantId(), answer.getSessionPublicId())
                    .ifPresent(state -> {
                        entityManager.lock(state, LockModeType.PESSIMISTIC_WRITE);
                        entityManager.refresh(state, LockModeType.PESSIMISTIC_WRITE);
                        if (state.getPublicationPublicId() != null) {
                            throw new ScoreSourceSelectionException(HttpStatus.CONFLICT,
                                    ScoreReviewConstants.PUBLISHED_LOCK, null,
                                    ScoreReviewConstants.PUBLISHED_LOCK_MESSAGE);
                        }
                    });
        }
        answer.setTeacherScore(score);
        answer.setTeacherScoredAt(Instant.now());
        scoringAnswerRepository.save(answer);
        log.debug("Host {} recorded teacherScore={} for answer {} (tenantId={})", caller.userId(), score,
                answerPublicId, caller.tenantId());
        return ScoringAnswerMapper.toResponse(answer);
    }

    private DecodedAnswerPayload withPresignedUrl(DecodedAnswerPayload decoded, UUID tenantId) {
        try {
            PresignedDownloadResponse presigned = mediaService.presignGet(decoded.mediaPublicId(),
                    MEDIA_URL_TTL_SECONDS, tenantId);
            return new DecodedAnswerPayload(decoded.kind(), decoded.text(), decoded.mediaPublicId(),
                    decoded.options(), presigned.url(), decoded.gapValues(), decoded.wordIndices());
        } catch (Exception ex) {
            log.warn("Media presign failed for mediaPublicId={} (tenantId={}) — showing answer without a playback link",
                    decoded.mediaPublicId(), tenantId, ex);
            return decoded;
        }
    }

    private static Integer attemptNumber(AttemptOwnerView owner) {
        return owner == null ? null : owner.attemptNumber();
    }

    private static boolean isFallback(AiDetailFlags flags) {
        return flags != null && flags.fallback();
    }

    private ScoringAnswerStatus parseStatus(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return null;
        }
        try {
            return ScoringAnswerStatus.valueOf(statusFilter.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidAnswerStatusException();
        }
    }

    private ScoringAnswerStatus parseSearchStatus(String search) {
        if (search == null) {
            return null;
        }
        try {
            return ScoringAnswerStatus.valueOf(search.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ScoringAnswer findOwned(UUID answerPublicId, CurrentUser caller) {
        ScoringAnswer answer = scoringAnswerRepository.findByAnswerPublicId(answerPublicId)
                .orElseThrow(AnswerNotFoundException::new);
        if (!caller.isPlatformUser() && !answer.getTenantId().equals(caller.tenantId())) {
            throw new AnswerNotFoundException();
        }
        return answer;
    }
}
