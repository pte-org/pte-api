package com.pte.scoring.internal.service.examiner;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptExaminerPromptView;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.media.MediaService;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.enums.ExaminerAnswerContentKind;
import com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus;
import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;
import com.pte.scoring.domain.enums.GradingCohortStatus;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.dto.response.AiScoreDetailResponse;
import com.pte.scoring.dto.response.ExaminerAiReferenceResponse;
import com.pte.scoring.dto.response.ExaminerAnswerDetailResponse;
import com.pte.scoring.dto.response.ExaminerAnswerPayloadResponse;
import com.pte.scoring.dto.response.ExaminerAttemptDetailResponse;
import com.pte.scoring.dto.response.ExaminerDecisionResponse;
import com.pte.scoring.dto.response.ExaminerPromptOptionResponse;
import com.pte.scoring.dto.response.ExaminerPromptResponse;
import com.pte.scoring.dto.response.ExaminerResponseOptionResponse;
import com.pte.scoring.dto.response.ExaminerWorkflowView;
import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.scoring.internal.dto.response.AnswerPayloadKind;
import com.pte.scoring.internal.dto.response.DecodedAnswerPayload;
import com.pte.scoring.internal.exception.examiner.ExaminerWorkNotFoundException;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.ExaminerWorkQueueRepository;
import com.pte.scoring.internal.service.ai.AiScoreDetailQueryService;
import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.internal.service.objective.AnswerContentReader;
import com.pte.scoring.internal.service.objective.AnswerPayloadDecoder;
import com.pte.scoring.internal.service.staff.ScoringEligibilityQueryService;
import com.pte.shared.security.CurrentUser;

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

/** Builds the assignment-scoped blind attempt detail shown to a verified Examiner. */
class ExaminerAttemptDetailReader {

    private final ExaminerWorkQueueRepository workQueueRepository;
    private final AiEvaluationRepository scoringAnswerRepository;
    private final ExaminerEvaluationRepository examinerEvaluationRepository;
    private final SessionGradingGateway gateway;
    private final ScoringEligibilityQueryService eligibilityQueryService;
    private final AnswerPayloadDecoder answerPayloadDecoder;
    private final AnswerContentReader contentReader;
    private final AttemptService attemptService;
    private final MediaService mediaService;
    private final ExaminerReviewVersionService examinerReviewVersionService;
    private final ExaminerWorkflowQueryService workflowQueryService;
    private final AiScoreDetailQueryService aiDetails;
    private final ExaminerSessionNames sessionNames;

    ExaminerAttemptDetailReader(ExaminerWorkQueueRepository workQueueRepository,
            AiEvaluationRepository scoringAnswerRepository,
            ExaminerEvaluationRepository examinerEvaluationRepository, SessionGradingGateway gateway,
            ScoringEligibilityQueryService eligibilityQueryService, AnswerPayloadDecoder answerPayloadDecoder,
            AnswerContentReader contentReader, AttemptService attemptService, MediaService mediaService,
            ExaminerReviewVersionService examinerReviewVersionService,
            ExaminerWorkflowQueryService workflowQueryService, AiScoreDetailQueryService aiDetails,
            ExaminerSessionNames sessionNames) {
        this.workQueueRepository = workQueueRepository;
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.examinerEvaluationRepository = examinerEvaluationRepository;
        this.gateway = gateway;
        this.eligibilityQueryService = eligibilityQueryService;
        this.answerPayloadDecoder = answerPayloadDecoder;
        this.contentReader = contentReader;
        this.attemptService = attemptService;
        this.mediaService = mediaService;
        this.examinerReviewVersionService = examinerReviewVersionService;
        this.workflowQueryService = workflowQueryService;
        this.aiDetails = aiDetails;
        this.sessionNames = sessionNames;
    }

    ExaminerAttemptDetailResponse read(UUID sessionPublicId, UUID attemptPublicId, CurrentUser examiner) {
        GradingAssignment assignment = workQueueRepository
                .findOwnedAttempt(examiner.tenantId(), sessionPublicId, attemptPublicId, examiner.userId())
                .orElseThrow(ExaminerWorkNotFoundException::new);

        ExaminerWorkflowView workflow = workflowQueryService != null
                ? workflowQueryService.buildWorkflowView(assignment, examiner.tenantId())
                : null;

        boolean isPublished = isSessionPublished(examiner.tenantId(), sessionPublicId);
        if (isPublished) {
            if (workflow == null || workflow.status() != ExaminerWorkflowStatus.COMPLETED) {
                throw new ExaminerWorkNotFoundException();
            }
        }

        List<AiEvaluation> eligibleAnswers = scoringAnswerRepository
                .findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionPublicId, examiner.tenantId(),
                        List.of(attemptPublicId))
                .stream()
                .filter(eligibilityQueryService::isAiEligible)
                .toList();
        if (eligibleAnswers.isEmpty()) {
            throw new ExaminerWorkNotFoundException();
        }

        SessionGradingGateway.Grading cohort = gateway.grading(examiner.tenantId(), sessionPublicId).orElse(null);
        AttemptGradingCandidateView member = cohort == null ? null
                : attemptService.getGradingCandidate(attemptPublicId, examiner.tenantId()).orElse(null);
        SessionGradingGateway.Publication sessionState = gateway.publication(examiner.tenantId(), sessionPublicId)
                .orElse(null);

        boolean sessionPublishedState = sessionState != null;
        String publicationState = sessionPublishedState ? "PUBLISHED" : "UNPUBLISHED";

        boolean attemptCanFinalize = !sessionPublishedState
                && cohort != null
                && cohort.markingMode() == GradingMarkingMode.MANUAL_EXAMINER
                && (cohort.status() == GradingCohortStatus.FROZEN || cohort.status() == GradingCohortStatus.COMPLETED)
                && (member != null && !member.excluded());

        String attemptBlockingReason = sessionPublishedState ? "ALREADY_PUBLISHED"
                : cohort == null ? "WAITING_FOR_HOST_FREEZE"
                : cohort.markingMode() != GradingMarkingMode.MANUAL_EXAMINER ? "COHORT_NOT_MANUAL_EXAMINER"
                : (cohort.status() != GradingCohortStatus.FROZEN && cohort.status() != GradingCohortStatus.COMPLETED) ? "WAITING_FOR_HOST_FREEZE"
                : (member == null || member.excluded()) ? "EXCLUDED_FROM_COHORT"
                : null;

        Map<UUID, ExaminerEvaluation> scoresByAnswer = examinerEvaluationRepository
                .findSubmittedByAttempt(examiner.tenantId(), sessionPublicId, attemptPublicId).stream()
                .collect(Collectors.toMap(ExaminerEvaluation::getAnswerPublicId, Function.identity()));
        Map<UUID, SubmittedAnswerView> contentByAnswer = contentReader.forAttempt(attemptPublicId,
                examiner.tenantId());
        List<UUID> pinnedItemPublicIds = eligibleAnswers.stream()
                .map(AiEvaluation::getItemPublicId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, AttemptExaminerPromptView> promptsByItem = attemptService
                .getExaminerPrompts(attemptPublicId, sessionPublicId, examiner.tenantId(), pinnedItemPublicIds);

        Map<UUID, DecodedAnswerPayload> decodedByAnswer = new HashMap<>();
        Set<UUID> mediaPublicIds = new LinkedHashSet<>();
        for (AiEvaluation answer : eligibleAnswers) {
            AttemptExaminerPromptView prompt = answer.getItemPublicId() == null
                    ? null : promptsByItem.get(answer.getItemPublicId());
            SubmittedAnswerView content = contentByAnswer.get(answer.getAnswerPublicId());
            if (prompt == null || content == null) {
                throw new ExaminerWorkNotFoundException();
            }
            DecodedAnswerPayload decoded = answerPayloadDecoder.decode(content);
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

        // Only reached after the assignment check, and keyed by the verified answers rather than a client id.
        Map<UUID, AiScoreDetailResponse> aiByAnswer = aiDetails == null ? Map.of()
                : aiDetails.findAll(examiner.tenantId(), eligibleAnswers);
        List<ExaminerAnswerDetailResponse> details = eligibleAnswers.stream()
                .map(answer -> toAnswerDetail(answer, scoresByAnswer.get(answer.getAnswerPublicId()),
                        promptsByItem.get(answer.getItemPublicId()),
                        decodedByAnswer.get(answer.getAnswerPublicId()),
                        contentByAnswer.get(answer.getAnswerPublicId()), mediaUrls, examiner,
                        assignment, cohort, member, sessionState, attemptCanFinalize, attemptBlockingReason,
                        aiByAnswer.getOrDefault(answer.getAnswerPublicId(), AiScoreDetailResponse.pending())))
                .sorted(Comparator.comparingInt(answer -> answer.prompt().orderIndex()))
                .toList();
        int submittedCount = (int) details.stream().filter(answer -> "SUBMITTED".equals(answer.status())).count();
        return new ExaminerAttemptDetailResponse(attemptPublicId, sessionPublicId, assignment.getAssignedAt(),
                assignment.getEligibleAnswerCount(), submittedCount, details,
                cohort != null ? cohort.markingMode().name() : null,
                cohort != null ? cohort.status().name() : null,
                publicationState, attemptCanFinalize, attemptBlockingReason, workflow,
                sessionNames.namesOf(List.of(sessionPublicId), examiner.tenantId()).get(sessionPublicId),
                (int) workQueueRepository.countOrdinal(examiner.tenantId(), examiner.userId(), sessionPublicId,
                        assignment.getAssignedAt(), assignment.getId()));
    }

    private ExaminerAnswerDetailResponse toAnswerDetail(AiEvaluation answer, ExaminerEvaluation savedScore,
            AttemptExaminerPromptView prompt, DecodedAnswerPayload decoded, SubmittedAnswerView content,
            Map<UUID, String> mediaUrls,
            CurrentUser examiner, GradingAssignment assignment, SessionGradingGateway.Grading cohort,
            AttemptGradingCandidateView member, SessionGradingGateway.Publication sessionState,
            boolean attemptCanFinalize, String attemptBlockingReason, AiScoreDetailResponse ai) {
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

        ExaminerAiReferenceResponse aiRef = examinerReviewVersionService.buildAiReference(answer);
        ExaminerDecisionResponse decision = savedScore != null
                ? new ExaminerDecisionResponse(savedScore.getStatus().name(), savedScore.getScore(),
                        savedScore.getDecisionKind(), savedScore.getSubmittedAt(),
                        savedScore.getReviewedAiScore(), savedScore.getReviewedAiVersion(),
                        savedScore.getRequestPublicId())
                : null;
        String reviewVersion = examinerReviewVersionService.computeReviewVersion(
                answer, content, assignment, cohort, member, savedScore, sessionState);
        boolean answerCanFinalize = attemptCanFinalize && savedScore == null;
        String answerBlockingReason = savedScore != null ? "ALREADY_FINALIZED" : attemptBlockingReason;

        return new ExaminerAnswerDetailResponse(answer.getAnswerPublicId(), answer.getTaskType(), safePrompt,
                safeResponse, savedScore == null ? "PENDING" : ExaminerAnswerScoreStatus.SUBMITTED.name(),
                savedScore == null ? null : savedScore.getScore(),
                savedScore == null ? null : savedScore.getSubmittedAt(),
                aiRef, decision, reviewVersion, answerCanFinalize, answerBlockingReason, ai);
    }

    private String mediaUrl(Map<UUID, String> mediaUrls, UUID mediaPublicId) {
        return mediaPublicId == null ? null : mediaUrls.get(mediaPublicId);
    }

    private boolean isSessionPublished(UUID tenantId, UUID sessionPublicId) {
        return gateway.isPublished(tenantId, sessionPublicId);
    }
}
