package com.pte.scoring.service;

import com.pte.common.security.CurrentUser;
import com.pte.scoring.client.MediaClient;
import com.pte.scoring.client.dto.MediaPresignedDownloadResponse;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.exception.AnswerNotFoundException;
import com.pte.scoring.domain.exception.InvalidAnswerStatusException;
import com.pte.scoring.dto.response.AnswerListItemResponse;
import com.pte.scoring.dto.response.AnswerListResponse;
import com.pte.scoring.dto.response.AnswerPayloadKind;
import com.pte.scoring.dto.response.AnswerReviewDetailResponse;
import com.pte.scoring.dto.response.DecodedAnswerPayload;
import com.pte.scoring.dto.response.ScoringAnswerResponse;
import com.pte.scoring.mapper.ScoringAnswerMapper;
import com.pte.scoring.repository.ScoringAnswerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Host-facing answer review (quang-host-answer-review): list, view full
 * decoded content + presigned media, and record an independent teacher
 * score. Tenant-scoped throughout; denial is 404, not 403 (no existence
 * leak, same pattern as reporting's {@code ReportService}).
 *
 * <p>No approval gate here anymore — the prior {@code approve()}/{@code
 * AI_SCORED_PENDING_REVIEW} hold-for-host-confirmation mechanism was removed
 * in Phase 5 (user decision: an AI score always finalizes on its own, same
 * as every other task type; a host's own score is independent data for
 * future AI-vs-teacher comparison stats, never a gate).
 */
@Service
public class ScoringReviewService {

    private static final Logger log = LoggerFactory.getLogger(ScoringReviewService.class);

    /** Fixed TTL for a review-screen playback link — no exam session window to derive it from here (unlike exam-delivery's StartAttempt-time presign), just a human clicking play. */
    private static final long MEDIA_URL_TTL_SECONDS = 3600;

    private final ScoringAnswerRepository scoringAnswerRepository;
    private final AnswerPayloadDecoder answerPayloadDecoder;
    private final MediaClient mediaClient;

    public ScoringReviewService(ScoringAnswerRepository scoringAnswerRepository,
                                AnswerPayloadDecoder answerPayloadDecoder, MediaClient mediaClient) {
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.answerPayloadDecoder = answerPayloadDecoder;
        this.mediaClient = mediaClient;
    }

    /**
     * Tenant-scoped answer review list (Phase 3) — a host is always
     * tenant-scoped ({@link com.pte.scoring.controller.ScoringReviewController}
     * grants only HOST_ADMIN/HOST_AUTHOR, never a platform role), so tenantId
     * always comes from {@code caller}, never a request parameter.
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
                        answer.getRawScore(), answer.getTeacherScore(), answer.getCreatedAt()))
                .toList();
        return new AnswerListResponse(items, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }

    /**
     * Full review detail for one answer (Phase 4) — tenant isolation reuses
     * {@link #findOwned}. Media presign failure never fails the whole
     * endpoint: the answer content is still visible, just without a
     * playback link.
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
                answer.getTeacherScore(), answer.getCreatedAt(), decoded);
    }

    /**
     * Records a host's own independent score (Phase 5) — never gated by
     * {@code status}, never touches {@code rawScore}, never emits {@code
     * AnswerScored} or calls {@code AttemptCompletionService}. Purely
     * parallel data for a future AI-vs-teacher comparison feature; which
     * score is "official" for student-facing reports is explicitly
     * undecided (out of scope here).
     */
    @Transactional
    public ScoringAnswerResponse submitTeacherScore(UUID answerPublicId, int score, CurrentUser caller) {
        ScoringAnswer answer = findOwned(answerPublicId, caller);
        answer.setTeacherScore(score);
        answer.setTeacherScoredAt(Instant.now());
        scoringAnswerRepository.save(answer);
        log.debug("Host {} recorded teacherScore={} for answer {} (tenantId={})", caller.userId(), score,
                answerPublicId, caller.tenantId());
        return ScoringAnswerMapper.toResponse(answer);
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
                    decoded.options(), presigned.url(), decoded.gapValues(), decoded.wordIndices());
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

    private ScoringAnswer findOwned(UUID answerPublicId, CurrentUser caller) {
        ScoringAnswer answer = scoringAnswerRepository.findByAnswerPublicId(answerPublicId)
                .orElseThrow(AnswerNotFoundException::new);
        if (!caller.isPlatformUser() && !answer.getTenantId().equals(caller.tenantId())) {
            throw new AnswerNotFoundException();
        }
        return answer;
    }
}
