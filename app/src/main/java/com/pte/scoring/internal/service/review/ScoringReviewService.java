package com.pte.scoring.internal.service.review;

import com.pte.scoring.internal.service.ai.AiScoreDetailQueryService;
import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.internal.service.objective.AnswerContentReader;
import com.pte.scoring.internal.service.objective.AnswerPayloadDecoder;
import com.pte.scoring.internal.service.staff.ScoringEligibilityQueryService;
import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptGradingCoverageView;
import com.pte.attempt.dto.response.AttemptOwnerView;
import com.pte.media.MediaService;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.enums.ScoringMethod;
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
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.shared.security.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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

    private final AiEvaluationRepository evaluationRepository;
    private final AnswerPayloadDecoder answerPayloadDecoder;
    private final MediaService mediaService;
    private final SessionGradingGateway gateway;
    private final EntityManager entityManager;
    private final AttemptService attemptService;
    private final AiScoreDetailQueryService aiDetails;
    private final ScoringEligibilityQueryService eligibility;
    private final com.pte.session.SessionService sessionService;
    private final AnswerContentReader contentReader;
    private final Clock clock;

    public ScoringReviewService(AiEvaluationRepository evaluationRepository,
            AnswerPayloadDecoder answerPayloadDecoder, MediaService mediaService,
            SessionGradingGateway gateway, EntityManager entityManager,
            AttemptService attemptService, AiScoreDetailQueryService aiDetails,
            ScoringEligibilityQueryService eligibility, com.pte.session.SessionService sessionService,
            AnswerContentReader contentReader,
            Clock clock) {
        this.contentReader = contentReader;
        this.evaluationRepository = evaluationRepository;
        this.answerPayloadDecoder = answerPayloadDecoder;
        this.mediaService = mediaService;
        this.gateway = gateway;
        this.entityManager = entityManager;
        this.attemptService = attemptService;
        this.aiDetails = aiDetails;
        this.eligibility = eligibility;
        this.sessionService = sessionService;
        this.clock = clock;
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
        boolean awaitingOnly = status == ScoringAnswerStatus.PENDING;
        AttemptGradingCoverageView coverage = attemptPublicId == null || attemptService == null ? null
                : attemptService.getGradingCoverage(List.of(attemptPublicId), sessionPublicId, caller.tenantId())
                        .get(attemptPublicId);
        Map<UUID, Integer> questionNumbers = questionNumbers(coverage);
        // One attempt holds a bounded set of answers; the question order is not a column, so it is sorted here.
        Pageable queryPage = attemptPublicId == null ? pageable : Pageable.unpaged();
        Page<AiEvaluation> found = normalizedSearch == null
                ? evaluationRepository.findForReview(caller.tenantId(), sessionPublicId, attemptPublicId, status,
                        awaitingOnly, queryPage)
                : evaluationRepository.findForReviewWithSearch(caller.tenantId(), sessionPublicId,
                        attemptPublicId, status, awaitingOnly, normalizedSearch, parseSearchStatus(normalizedSearch),
                        parseUuid(normalizedSearch), queryPage);
        Page<AiEvaluation> page = attemptPublicId == null ? found
                : byQuestionNumber(found.getContent(), questionNumbers, pageable);
        log.debug("Host {} listed answers (tenantId={}, sessionPublicId={}, status={}) -> {} results",
                caller.userId(), caller.tenantId(), sessionPublicId, status, page.getNumberOfElements());
        Map<UUID, AttemptOwnerView> attemptOwners = attemptService == null ? Map.of()
                : attemptService.getAttemptOwners(sessionPublicId, caller.tenantId(), page.getContent().stream()
                        .map(AiEvaluation::getAttemptPublicId).distinct().toList());
        Map<UUID, AiDetailFlags> aiFlags = aiDetails == null ? Map.of()
                : aiDetails.findFlags(caller.tenantId(), page.getContent().stream()
                        .map(AiEvaluation::getAnswerPublicId).toList());
        List<AnswerListItemResponse> items = page.getContent().stream()
                .map(answer -> new AnswerListItemResponse(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                        answer.getSessionPublicId(), answer.getTaskType(), answer.getStatus().name(),
                        answer.getRawScore(), answer.getTeacherScore(), answer.getCreatedAt(),
                        attemptNumber(attemptOwners.get(answer.getAttemptPublicId())),
                        isFallback(aiFlags.get(answer.getAnswerPublicId())),
                        eligibility != null && eligibility.isAiEligible(answer),
                        isUnscored(answer), questionNumbers.get(answer.getItemPublicId())))
                .toList();
        int[] counts = coverageCounts(coverage, sessionPublicId, attemptPublicId, caller);
        return new AnswerListResponse(items, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), counts == null ? null : counts[0], counts == null ? null : counts[1]);
    }

    /** Ascending question number (answers of unnumbered items last), sliced to the requested page. */
    private static Page<AiEvaluation> byQuestionNumber(List<AiEvaluation> answers,
            Map<UUID, Integer> questionNumbers, Pageable pageable) {
        List<AiEvaluation> sorted = answers.stream()
                .sorted(java.util.Comparator.comparing((AiEvaluation answer) -> questionNumbers.get(answer.getItemPublicId()),
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
        int from = (int) Math.min(pageable.getOffset(), sorted.size());
        int to = Math.min(from + pageable.getPageSize(), sorted.size());
        return new org.springframework.data.domain.PageImpl<>(sorted.subList(from, to), pageable, sorted.size());
    }

    /** Exam items by 1-based position, so the host can tell apart answers of the same task type. */
    private static Map<UUID, Integer> questionNumbers(AttemptGradingCoverageView coverage) {
        Map<UUID, Integer> numbers = new java.util.HashMap<>();
        if (coverage != null) {
            coverage.expectedItems().stream().filter(item -> item.orderIndex() != null)
                    .forEach(item -> numbers.put(item.itemPublicId(), item.orderIndex() + 1));
        }
        return numbers;
    }

    /** {expected, answered}: the exam's items for the attempt and how many of them the student answered. */
    private int[] coverageCounts(AttemptGradingCoverageView coverage, UUID sessionPublicId, UUID attemptPublicId,
            CurrentUser caller) {
        if (coverage == null) {
            return null;
        }
        java.util.Set<UUID> answeredItems = evaluationRepository
                .findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndDeletedFalse(caller.tenantId(),
                        sessionPublicId, attemptPublicId).stream()
                .map(AiEvaluation::getItemPublicId).collect(java.util.stream.Collectors.toSet());
        int answered = (int) coverage.expectedItems().stream()
                .filter(item -> answeredItems.contains(item.itemPublicId())).count();
        return new int[] {coverage.expectedItems().size(), answered};
    }

    /**
     * Full review detail for one answer — tenant isolation reuses {@link
     * #findOwned}. Media presign failure never fails the whole endpoint: the
     * answer content is still visible, just without a playback link.
     */
    @Transactional(readOnly = true)
    public AnswerReviewDetailResponse getAnswerForReview(UUID answerPublicId, CurrentUser caller) {
        AiEvaluation answer = findOwned(answerPublicId, caller);
        DecodedAnswerPayload decoded = answerPayloadDecoder.decode(contentReader.one(answerPublicId, answer.getTenantId())
                .orElseThrow(AnswerNotFoundException::new));
        if (decoded.kind() == AnswerPayloadKind.AUDIO && decoded.mediaPublicId() != null) {
            decoded = withPresignedUrl(decoded, answer.getTenantId());
        }
        return new AnswerReviewDetailResponse(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getSessionPublicId(), answer.getTaskType(), answer.getStatus().name(), answer.getRawScore(),
                answer.getTeacherScore(), answer.getCreatedAt(), decoded,
                aiDetails == null ? null : aiDetails.find(answer), isUnscored(answer));
    }

    private static boolean isUnscored(AiEvaluation answer) {
        return answer.getScoringMethod() == null || answer.getScoringMethod() == ScoringMethod.UNSCORED;
    }

    /**
     * Records a host's own independent score — never gated by {@code
     * status}, never touches {@code rawScore}. Purely parallel data for a
     * future AI-vs-teacher comparison feature; which score is "official" for
     * student-facing reports is undecided (out of scope here).
     */
    @Transactional
    public ScoringAnswerResponse submitTeacherScore(UUID answerPublicId, int score, CurrentUser caller) {
        AiEvaluation answer = findOwned(answerPublicId, caller);
        if (sessionService != null) {
            sessionService.lockForScoreReviewMutation(answer.getSessionPublicId(), caller.tenantId());
        }
        gateway.grading(caller.tenantId(), answer.getSessionPublicId())
                .filter(grading -> grading.markingMode() == com.pte.scoring.domain.enums.GradingMarkingMode.MANUAL_EXAMINER)
                .ifPresent(grading -> {
                    throw new ScoreSourceSelectionException(HttpStatus.CONFLICT,
                            ScoreReviewConstants.MANUAL_EXAMINER_SELECTION_PROHIBITED, null,
                            ScoreReviewConstants.MANUAL_EXAMINER_SELECTION_PROHIBITED_MESSAGE);
                });
        if (entityManager != null) {
            entityManager.lock(answer, LockModeType.PESSIMISTIC_WRITE);
            entityManager.refresh(answer, LockModeType.PESSIMISTIC_WRITE);
        }
        if (gateway.isPublished(caller.tenantId(), answer.getSessionPublicId())) {
            throw new ScoreSourceSelectionException(HttpStatus.CONFLICT, ScoreReviewConstants.PUBLISHED_LOCK, null,
                    ScoreReviewConstants.PUBLISHED_LOCK_MESSAGE);
        }
        answer.setTeacherScore(score);
        answer.setTeacherScoredAt(clock.instant());
        evaluationRepository.save(answer);
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

    private AiEvaluation findOwned(UUID answerPublicId, CurrentUser caller) {
        AiEvaluation answer = evaluationRepository.findByAnswerPublicId(answerPublicId)
                .orElseThrow(AnswerNotFoundException::new);
        if (!caller.isPlatformUser() && !answer.getTenantId().equals(caller.tenantId())) {
            throw new AnswerNotFoundException();
        }
        return answer;
    }
}
