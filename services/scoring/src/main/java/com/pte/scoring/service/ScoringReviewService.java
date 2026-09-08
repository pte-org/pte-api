package com.pte.scoring.service;

import com.pte.common.security.CurrentUser;
import com.pte.scoring.client.MediaClient;
import com.pte.scoring.client.dto.MediaPresignedDownloadResponse;
import com.pte.scoring.constant.ScoringConstants;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.event.AnswerScoredEvent;
import com.pte.scoring.domain.exception.AnswerNotFoundException;
import com.pte.scoring.domain.exception.InvalidAnswerStatusException;
import com.pte.scoring.domain.exception.ReviewNotPendingException;
import com.pte.scoring.dto.response.AnswerListItemResponse;
import com.pte.scoring.dto.response.AnswerListResponse;
import com.pte.scoring.dto.response.AnswerPayloadKind;
import com.pte.scoring.dto.response.AnswerReviewDetailResponse;
import com.pte.scoring.dto.response.DecodedAnswerPayload;
import com.pte.scoring.dto.response.ScoringAnswerResponse;
import com.pte.scoring.mapper.ScoringAnswerMapper;
import com.pte.scoring.messaging.outbox.OutboxWriter;
import com.pte.scoring.repository.ScoringAnswerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The human-review gate (phase-09): a host must explicitly approve an
 * AI-scored answer of a review-required task type (Write Essay — one of
 * Pearson's 7 sensitive types) before it counts as final. Tenant-scoped;
 * denial is 404, not 403 (no existence leak, same pattern as reporting).
 */
@Service
public class ScoringReviewService {

    private static final Logger log = LoggerFactory.getLogger(ScoringReviewService.class);

    /** Fixed TTL for a review-screen playback link — no exam session window to derive it from here (unlike exam-delivery's StartAttempt-time presign), just a human clicking play. */
    private static final long MEDIA_URL_TTL_SECONDS = 3600;

    private final ScoringAnswerRepository scoringAnswerRepository;
    private final OutboxWriter outboxWriter;
    private final AttemptCompletionService attemptCompletionService;
    private final AnswerPayloadDecoder answerPayloadDecoder;
    private final MediaClient mediaClient;

    public ScoringReviewService(ScoringAnswerRepository scoringAnswerRepository, OutboxWriter outboxWriter,
                                AttemptCompletionService attemptCompletionService,
                                AnswerPayloadDecoder answerPayloadDecoder, MediaClient mediaClient) {
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.outboxWriter = outboxWriter;
        this.attemptCompletionService = attemptCompletionService;
        this.answerPayloadDecoder = answerPayloadDecoder;
        this.mediaClient = mediaClient;
    }

    /**
     * Tenant-scoped answer review list (quang-host-answer-review Phase 3) — a
     * host is always tenant-scoped ({@link com.pte.scoring.controller.ScoringReviewController}
     * grants only HOST_ADMIN/HOST_AUTHOR, never a platform role), so tenantId
     * always comes from {@code caller}, never a request parameter — the same
     * "tenant ID is not client-suppliable" rule the detail endpoint (Phase 4)
     * and {@code ReportService} both follow.
     */
    @Transactional(readOnly = true)
    public AnswerListResponse listAnswers(UUID sessionPublicId, String statusFilter, Pageable pageable,
                                          CurrentUser caller) {
        ScoringAnswerStatus status = parseStatus(statusFilter);
        Page<ScoringAnswer> page = scoringAnswerRepository.findForReview(caller.tenantId(), sessionPublicId, status,
                pageable);
        log.debug("Host {} listed answers (tenantId={}, sessionPublicId={}, status={}) -> {} results",
                caller.userId(), caller.tenantId(), sessionPublicId, status, page.getNumberOfElements());
        List<AnswerListItemResponse> items = page.getContent().stream()
                .map(answer -> new AnswerListItemResponse(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                        answer.getSessionPublicId(), answer.getTaskType(), answer.getStatus().name(),
                        answer.getRawScore(), answer.getCreatedAt()))
                .toList();
        return new AnswerListResponse(items, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }

    /**
     * Full review detail for one answer (quang-host-answer-review Phase 4) —
     * tenant isolation reuses {@link #findOwned}, same 404-not-403 rule as
     * {@code approve} and {@code ReportService.canView}. Media presign
     * failure never fails the whole endpoint (Design Constraint): the answer
     * content is still visible, just without a playback link.
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
                answer.getCreatedAt(), decoded);
    }

    private DecodedAnswerPayload withPresignedUrl(DecodedAnswerPayload decoded, UUID tenantId) {
        try {
            MediaPresignedDownloadResponse presigned = mediaClient.presignGet(decoded.mediaPublicId(),
                    MEDIA_URL_TTL_SECONDS, tenantId);
            if (presigned == null) {
                log.warn("Media presign returned null for mediaPublicId={} (tenantId={}) — showing answer without a playback link",
                        decoded.mediaPublicId(), tenantId);
                return decoded;
            }
            return new DecodedAnswerPayload(decoded.kind(), decoded.text(), decoded.mediaPublicId(),
                    decoded.options(), presigned.url());
        } catch (Exception ex) {
            log.warn("Media presign failed for mediaPublicId={} (tenantId={}) — showing answer without a playback link",
                    decoded.mediaPublicId(), tenantId, ex);
            return decoded;
        }
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

    @Transactional
    public ScoringAnswerResponse approve(UUID answerPublicId, CurrentUser caller) {
        ScoringAnswer answer = findOwned(answerPublicId, caller);
        if (answer.getStatus() != ScoringAnswerStatus.AI_SCORED_PENDING_REVIEW) {
            throw new ReviewNotPendingException();
        }

        int finalScore = answer.getRawScore();
        answer.markScored(finalScore);
        scoringAnswerRepository.save(answer);

        outboxWriter.write(ScoringConstants.AGGREGATE_ANSWER, answer.getAnswerPublicId().toString(),
                ScoringConstants.EVENT_ANSWER_SCORED,
                new AnswerScoredEvent(answer.getAttemptPublicId(), answer.getAnswerPublicId(), answer.getTenantId(), finalScore),
                answer.getTenantId());
        attemptCompletionService.checkAndEmitIfComplete(answer.getAttemptPublicId(), answer.getSessionPublicId(), answer.getTenantId());

        return ScoringAnswerMapper.toResponse(answer);
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
