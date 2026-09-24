package com.pte.scoring.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.ExaminerQuestionPromptView;
import com.pte.identity.IdentityService;
import com.pte.media.MediaService;
import com.pte.scoring.domain.ExaminerAnswerScore;
import com.pte.scoring.domain.ExaminerAttemptAssignment;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.ScoringSessionState;
import com.pte.scoring.domain.enums.ExaminerAnswerContentKind;
import com.pte.scoring.domain.enums.ExaminerQueueStatus;
import com.pte.scoring.dto.request.SubmitExaminerScoreRequest;
import com.pte.scoring.dto.response.ExaminerAnswerDetailResponse;
import com.pte.scoring.dto.response.ExaminerAnswerPayloadResponse;
import com.pte.scoring.dto.response.ExaminerAttemptDetailResponse;
import com.pte.scoring.dto.response.ExaminerPromptOptionResponse;
import com.pte.scoring.dto.response.ExaminerPromptResponse;
import com.pte.scoring.dto.response.ExaminerQueueResponse;
import com.pte.scoring.dto.response.ExaminerResponseOptionResponse;
import com.pte.scoring.dto.response.ExaminerScoreSubmissionResponse;
import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.scoring.internal.dto.response.AnswerPayloadKind;
import com.pte.scoring.internal.dto.response.DecodedAnswerPayload;
import com.pte.scoring.internal.exception.ExaminerScoreConflictException;
import com.pte.scoring.internal.exception.ExaminerWorkNotFoundException;
import com.pte.scoring.internal.exception.InvalidExaminerScoreException;
import com.pte.scoring.internal.exception.InvalidExaminerQueueStatusException;
import com.pte.scoring.internal.repository.ExaminerAnswerScoreRepository;
import com.pte.scoring.internal.repository.ExaminerAttemptAssignmentRepository;
import com.pte.scoring.internal.repository.ExaminerWorkQueueRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
import com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus;
import com.pte.shared.security.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Owns Examiner queue, assignment-scoped blind detail, and immutable score submission. */
@Service
public class ExaminerScoringService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ExaminerWorkQueueRepository workQueueRepository;
    private final ExaminerAttemptAssignmentRepository assignmentRepository;
    private final ScoringAnswerRepository scoringAnswerRepository;
    private final ExaminerAnswerScoreRepository examinerAnswerScoreRepository;
    private final ScoringSessionStateRepository sessionStateRepository;
    private final ScoringEligibilityQueryService eligibilityQueryService;
    private final AnswerPayloadDecoder answerPayloadDecoder;
    private final IdentityService identityService;
    private final AssessmentService assessmentService;
    private final MediaService mediaService;
    private final EntityManager entityManager;

    public ExaminerScoringService(ExaminerWorkQueueRepository workQueueRepository,
            ExaminerAttemptAssignmentRepository assignmentRepository,
            ScoringAnswerRepository scoringAnswerRepository,
            ExaminerAnswerScoreRepository examinerAnswerScoreRepository,
            ScoringSessionStateRepository sessionStateRepository,
            ScoringEligibilityQueryService eligibilityQueryService,
            AnswerPayloadDecoder answerPayloadDecoder, IdentityService identityService,
            AssessmentService assessmentService, MediaService mediaService, EntityManager entityManager) {
        this.workQueueRepository = workQueueRepository;
        this.assignmentRepository = assignmentRepository;
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.examinerAnswerScoreRepository = examinerAnswerScoreRepository;
        this.sessionStateRepository = sessionStateRepository;
        this.eligibilityQueryService = eligibilityQueryService;
        this.answerPayloadDecoder = answerPayloadDecoder;
        this.identityService = identityService;
        this.assessmentService = assessmentService;
        this.mediaService = mediaService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public ExaminerQueueResponse listWork(String requestedStatus, UUID sessionPublicId, Integer requestedPage,
            Integer requestedSize, CurrentUser caller) {
        CurrentUser examiner = requireActiveExaminer(caller);
        ExaminerQueueStatus status = parseStatus(requestedStatus);
        int page = requestedPage == null || requestedPage < 0 ? 0 : requestedPage;
        int size = requestedSize == null || requestedSize <= 0 ? DEFAULT_PAGE_SIZE
                : Math.min(requestedSize, MAX_PAGE_SIZE);
        Page<com.pte.scoring.dto.response.ExaminerQueueItemResponse> result = workQueueRepository.findQueue(
                examiner.tenantId(), examiner.userId(), sessionPublicId, status.name(), PageRequest.of(page, size));
        return new ExaminerQueueResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ExaminerAttemptDetailResponse getAttempt(UUID sessionPublicId, UUID attemptPublicId, CurrentUser caller) {
        CurrentUser examiner = requireActiveExaminer(caller);
        if (isSessionPublished(examiner.tenantId(), sessionPublicId)) {
            throw new ExaminerWorkNotFoundException();
        }
        ExaminerAttemptAssignment assignment = workQueueRepository
                .findOwnedAttempt(examiner.tenantId(), sessionPublicId, attemptPublicId, examiner.userId())
                .orElseThrow(ExaminerWorkNotFoundException::new);

        List<ScoringAnswer> eligibleAnswers = scoringAnswerRepository
                .findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionPublicId, examiner.tenantId(),
                        List.of(attemptPublicId))
                .stream()
                .filter(eligibilityQueryService::isAiEligible)
                .toList();
        if (eligibleAnswers.isEmpty()) {
            throw new ExaminerWorkNotFoundException();
        }

        Map<UUID, ExaminerAnswerScore> scoresByAnswer = examinerAnswerScoreRepository
                .findByTenantIdAndSessionPublicIdAndAttemptPublicId(
                        examiner.tenantId(), sessionPublicId, attemptPublicId).stream()
                .collect(Collectors.toMap(ExaminerAnswerScore::getAnswerPublicId, Function.identity()));
        List<UUID> pinnedItemPublicIds = eligibleAnswers.stream()
                .map(ScoringAnswer::getPinnedItemPublicId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, ExaminerQuestionPromptView> promptsByItem = assessmentService
                .getExaminerPrompts(pinnedItemPublicIds, examiner.tenantId());

        Map<UUID, DecodedAnswerPayload> decodedByAnswer = new HashMap<>();
        Set<UUID> mediaPublicIds = new LinkedHashSet<>();
        for (ScoringAnswer answer : eligibleAnswers) {
            ExaminerQuestionPromptView prompt = answer.getPinnedItemPublicId() == null
                    ? null : promptsByItem.get(answer.getPinnedItemPublicId());
            if (prompt == null) {
                throw new ExaminerWorkNotFoundException();
            }
            DecodedAnswerPayload decoded = answerPayloadDecoder.decode(answer);
            decodedByAnswer.put(answer.getAnswerPublicId(), decoded);
            if (decoded.kind() == AnswerPayloadKind.AUDIO && decoded.mediaPublicId() != null) {
                mediaPublicIds.add(decoded.mediaPublicId());
            }
            if (prompt.audioPromptRef() != null) {
                mediaPublicIds.add(prompt.audioPromptRef());
            }
            if (prompt.imagePromptRef() != null) {
                mediaPublicIds.add(prompt.imagePromptRef());
            }
        }
        Map<UUID, String> mediaUrls = mediaPublicIds.isEmpty() ? Map.of()
                : mediaService.presignGetAll(mediaPublicIds, ExaminerScoringConstants.MEDIA_URL_TTL_SECONDS,
                        examiner.tenantId()).entrySet().stream()
                        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().url()));

        List<ExaminerAnswerDetailResponse> details = eligibleAnswers.stream()
                .map(answer -> toAnswerDetail(answer, scoresByAnswer.get(answer.getAnswerPublicId()),
                        promptsByItem.get(answer.getPinnedItemPublicId()),
                        decodedByAnswer.get(answer.getAnswerPublicId()), mediaUrls, examiner))
                .sorted(Comparator.comparingInt(answer -> answer.prompt().orderIndex()))
                .toList();
        int submittedCount = (int) details.stream().filter(answer -> "SUBMITTED".equals(answer.status())).count();
        return new ExaminerAttemptDetailResponse(attemptPublicId, sessionPublicId, assignment.getAssignedAt(),
                assignment.getEligibleAnswerCount(), submittedCount, details);
    }

    @Transactional
    public ExaminerScoreSubmissionResponse submitScore(UUID answerPublicId, SubmitExaminerScoreRequest request,
            CurrentUser caller) {
        CurrentUser examiner = requireActiveExaminer(caller);
        if (request == null || request.score() == null || request.score() < 0 || request.score() > 100) {
            throw new InvalidExaminerScoreException();
        }
        ScoringAnswer answer = scoringAnswerRepository.findByAnswerPublicId(answerPublicId)
                .orElseThrow(ExaminerWorkNotFoundException::new);
        if (!answer.getTenantId().equals(examiner.tenantId())
                || !assignmentRepository.existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicId(
                        examiner.tenantId(), answer.getSessionPublicId(), answer.getAttemptPublicId(),
                        examiner.userId())
                || !eligibilityQueryService.isAiEligible(answer)) {
            throw new ExaminerWorkNotFoundException();
        }

        entityManager.lock(answer, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(answer, LockModeType.PESSIMISTIC_WRITE);
        lockSessionPublicationIfPresent(answer.getTenantId(), answer.getSessionPublicId());

        ExaminerAnswerScore existing = examinerAnswerScoreRepository
                .findByAnswerPublicIdAndTenantId(answerPublicId, examiner.tenantId()).orElse(null);
        if (existing != null) {
            if (existing.getExaminerPublicId().equals(examiner.userId()) && existing.getScore() == request.score()) {
                return toSubmission(existing);
            }
            throw new ExaminerScoreConflictException();
        }

        ExaminerAnswerScore saved = examinerAnswerScoreRepository.saveAndFlush(new ExaminerAnswerScore(
                answer.getAnswerPublicId(), answer.getAttemptPublicId(), answer.getSessionPublicId(),
                answer.getTenantId(), examiner.userId(), request.score(), Instant.now()));
        return toSubmission(saved);
    }

    private ExaminerAnswerDetailResponse toAnswerDetail(ScoringAnswer answer, ExaminerAnswerScore savedScore,
            ExaminerQuestionPromptView prompt, DecodedAnswerPayload decoded, Map<UUID, String> mediaUrls,
            CurrentUser examiner) {
        if (savedScore != null && !savedScore.getExaminerPublicId().equals(examiner.userId())) {
            throw new ExaminerWorkNotFoundException();
        }
        if (prompt == null || decoded == null) {
            throw new ExaminerWorkNotFoundException();
        }
        String answerMediaUrl = decoded.kind() == AnswerPayloadKind.AUDIO
                ? mediaUrl(mediaUrls, decoded.mediaPublicId()) : null;
        ExaminerPromptResponse safePrompt = new ExaminerPromptResponse(prompt.orderIndex(), prompt.section(),
                prompt.taskType(), prompt.title(), prompt.promptText(),
                mediaUrl(mediaUrls, prompt.audioPromptRef()), mediaUrl(mediaUrls, prompt.imagePromptRef()),
                prompt.minWordCount(), prompt.maxWordCount(),
                prompt.options().stream().map(option -> new ExaminerPromptOptionResponse(
                        option.orderIndex(), option.text(), option.blankIndex())).toList());
        ExaminerAnswerPayloadResponse safeResponse = new ExaminerAnswerPayloadResponse(
                ExaminerAnswerContentKind.valueOf(decoded.kind().name()), decoded.text(), answerMediaUrl,
                decoded.options() == null ? List.of() : decoded.options().stream()
                        .map(option -> new ExaminerResponseOptionResponse(
                                option.orderIndex(), option.text(), option.selectedByStudent()))
                        .toList(),
                decoded.gapValues(), decoded.wordIndices());
        return new ExaminerAnswerDetailResponse(answer.getAnswerPublicId(), answer.getTaskType(), safePrompt,
                safeResponse, savedScore == null ? "PENDING" : ExaminerAnswerScoreStatus.SUBMITTED.name(),
                savedScore == null ? null : savedScore.getScore(),
                savedScore == null ? null : savedScore.getSubmittedAt());
    }

    private String mediaUrl(Map<UUID, String> mediaUrls, UUID mediaPublicId) {
        return mediaPublicId == null ? null : mediaUrls.get(mediaPublicId);
    }

    private void lockSessionPublicationIfPresent(UUID tenantId, UUID sessionPublicId) {
        sessionStateRepository.findByTenantIdAndSessionPublicId(tenantId, sessionPublicId).ifPresent(state -> {
            entityManager.lock(state, LockModeType.PESSIMISTIC_WRITE);
            entityManager.refresh(state, LockModeType.PESSIMISTIC_WRITE);
            if (state.getPublicationPublicId() != null) {
                throw new ExaminerScoreConflictException();
            }
        });
    }

    private boolean isSessionPublished(UUID tenantId, UUID sessionPublicId) {
        return sessionStateRepository.findByTenantIdAndSessionPublicId(tenantId, sessionPublicId)
                .map(state -> state.getPublicationPublicId() != null)
                .orElse(false);
    }

    private ExaminerScoreSubmissionResponse toSubmission(ExaminerAnswerScore score) {
        return new ExaminerScoreSubmissionResponse(score.getAnswerPublicId(), score.getScore(),
                score.getStatus().name(), score.getSubmittedAt());
    }

    private ExaminerQueueStatus parseStatus(String requestedStatus) {
        if (requestedStatus == null || requestedStatus.isBlank()) {
            return ExaminerQueueStatus.ALL;
        }
        try {
            return ExaminerQueueStatus.valueOf(requestedStatus.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidExaminerQueueStatusException();
        }
    }

    private CurrentUser requireActiveExaminer(CurrentUser caller) {
        if (caller == null || caller.userId() == null || caller.tenantId() == null
                || !caller.hasRole("EXAMINER")) {
            throw new AccessDeniedException(ExaminerScoringConstants.AUTHENTICATED_TENANT_EXAMINER_REQUIRED);
        }
        if (identityService.findActiveExaminers(caller.tenantId(), List.of(caller.userId())).isEmpty()) {
            throw new ExaminerWorkNotFoundException();
        }
        return caller;
    }
}
