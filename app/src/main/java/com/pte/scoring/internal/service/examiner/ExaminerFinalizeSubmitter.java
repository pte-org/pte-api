package com.pte.scoring.internal.service.examiner;

import com.pte.scoring.internal.service.examiner.trait.ExaminerTraitScoreResolver;
import com.pte.attempt.AttemptService;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.enums.ExaminerAiAvailability;
import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import com.pte.scoring.dto.request.FinalizeExaminerWorkRequest;
import com.pte.scoring.dto.response.ExaminerAiReferenceResponse;
import com.pte.scoring.dto.response.ExaminerCommandReceiptResponse;
import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Promotes an examiner's drafts to submitted scores when the work is finalized, and selects those scores as the
 * scores of record. Runs inside the finalize transaction, after the caller took the session and assignment locks.
 */
@Component
class ExaminerFinalizeSubmitter {

    private final ExaminerEvaluationRepository evaluationRepository;
    private final ExaminerReviewVersionService examinerReviewVersionService;
    private final AttemptService attemptService;
    private final ExaminerTraitScoreResolver traits;
    private final com.pte.scoring.internal.service.examiner.trait.ExaminerTraitFreshness freshness;

    ExaminerFinalizeSubmitter(ExaminerEvaluationRepository evaluationRepository,
            ExaminerReviewVersionService examinerReviewVersionService, AttemptService attemptService, ExaminerTraitScoreResolver traits,
            com.pte.scoring.internal.service.examiner.trait.ExaminerTraitFreshness freshness) {
        this.evaluationRepository = evaluationRepository;
        this.examinerReviewVersionService = examinerReviewVersionService;
        this.attemptService = attemptService;
        this.traits = traits;
        this.freshness = freshness;
    }

    List<ExaminerCommandReceiptResponse.ChildRequestReceipt> submitAll(CurrentUser user, UUID sessionPublicId,
            UUID attemptPublicId, GradingAssignment work, FinalizeExaminerWorkRequest request,
            List<AiEvaluation> eligibleAnswers, Instant now) {
        Map<UUID, ExaminerEvaluation> draftsByAnswer = evaluationRepository
                .findByTenantIdAndAssignmentIdAndDeletedFalse(user.tenantId(), work.getId())
                .stream()
                .collect(Collectors.toMap(ExaminerEvaluation::getAnswerPublicId, Function.identity()));

        if (draftsByAnswer.values().stream().anyMatch(ExaminerEvaluation::isCarriedOver)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    ExaminerScoringConstants.CARRIED_OVER_DRAFTS_UNCONFIRMED);
        }

        Map<UUID, ExaminerEvaluation> existingScores = evaluationRepository
                .findSubmittedByAttempt(user.tenantId(), sessionPublicId, attemptPublicId)
                .stream()
                .collect(Collectors.toMap(ExaminerEvaluation::getAnswerPublicId, Function.identity()));

        Map<UUID, AiEvaluation> answersById = eligibleAnswers.stream()
                .collect(Collectors.toMap(AiEvaluation::getAnswerPublicId, Function.identity()));

        List<ExaminerCommandReceiptResponse.ChildRequestReceipt> childReceipts = new ArrayList<>();
        Map<UUID, UUID> scoreByAnswer = new java.util.LinkedHashMap<>();

        for (FinalizeExaminerWorkRequest.FinalizeAnswerItemRequest item : request.answers()) {
            if (existingScores.containsKey(item.answerPublicId())) {
                // Preserved legacy final score
                scoreByAnswer.put(item.answerPublicId(), existingScores.get(item.answerPublicId()).getPublicId());
                continue;
            }

            ExaminerEvaluation draft = draftsByAnswer.get(item.answerPublicId());
            if (draft == null || draft.getScore() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ExaminerScoringConstants.DRAFT_INCOMPLETE_FORMAT.formatted(item.answerPublicId()));
            }
            if (item.expectedDraftVersion() != draft.getDraftVersion()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.DRAFT_STALE_FORMAT.formatted(item.answerPublicId()));
            }

            AiEvaluation answerEntity = answersById.get(item.answerPublicId());
            freshness.requireCurrent(answerEntity, work, item.expectedReviewVersion());
            var calculated = traits.stored(answerEntity, draft);
            if (!Objects.equals(item.expectedReviewVersion(), draft.getExpectedReviewVersion())
                    || !Objects.equals(item.reviewedAiVersion(), draft.getReviewedAiVersion()))
                throw new com.pte.scoring.internal.exception.examiner.StaleReviewVersionException();

            UUID childUUID = UUID.nameUUIDFromBytes((ExaminerScoringConstants.FINALIZE_CHILD_KEY_PREFIX + request.requestPublicId() + ":" + item.answerPublicId()).getBytes(StandardCharsets.UTF_8));
            String childFingerprint = ExaminerWorkRecorder.fingerprint(ExaminerScoringConstants.FINGERPRINT_SCORE + user.tenantId() + ":" + item.answerPublicId() + ":" + childUUID + ":" + draft.getScore() + ":" + draft.getTraitScoresJson() + ":" + draft.getRubricVersionPublicId());

            ExaminerDecisionKind decisionKind = draft.getDecisionKind() != null ? draft.getDecisionKind() : ExaminerDecisionKind.MANUAL;
            Integer reviewedAiScore = null;
            if (decisionKind == ExaminerDecisionKind.CONFIRM_AI) {
                ExaminerAiReferenceResponse aiRef = examinerReviewVersionService.buildAiReference(answerEntity);
                if (aiRef.availability() != ExaminerAiAvailability.AVAILABLE || aiRef.score() == null
                        || !Objects.equals(aiRef.version(), draft.getReviewedAiVersion())
                        || !Objects.equals(aiRef.score(), calculated.itemScore()) || !traits.matchesAi(answerEntity, calculated)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            ExaminerScoringConstants.CONFIRM_AI_FINALIZE_MISMATCH_FORMAT.formatted(item.answerPublicId()));
                }
                reviewedAiScore = aiRef.score();
            }

            draft.submit(draft.getScore(), now, decisionKind, reviewedAiScore, draft.getReviewedAiVersion(),
                    draft.getExpectedReviewVersion(), childUUID, childFingerprint);
            evaluationRepository.save(draft);
            scoreByAnswer.put(item.answerPublicId(), draft.getPublicId());
            childReceipts.add(new ExaminerCommandReceiptResponse.ChildRequestReceipt(item.answerPublicId(), childUUID, calculated.itemScore(), calculated.scoreTrace()));
        }
        // In a manual session the examiner's submitted score is the score of record, so selecting it is part of finalizing.
        attemptService.selectScoreSource(scoreByAnswer.keySet(), user.tenantId(), ExaminerScoringConstants.EXAMINER_SOURCE, user.userId(), now,
                scoreByAnswer);

        return childReceipts;
    }
}
