package com.pte.itembank;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.domain.enums.Visibility;
import com.pte.itembank.dto.response.PracticeQuestionContent;
import com.pte.itembank.dto.response.PracticeQuestionEvaluationContent;
import com.pte.itembank.dto.response.PracticeQuestionPin;
import com.pte.itembank.dto.response.QuestionFreezeView;
import com.pte.itembank.internal.repository.QuestionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public itembank facade for the standalone practice module.
 *
 * <p>Only APPROVED, current, shared questions marked for practice are exposed,
 * and the student projection deliberately removes scoring keys.</p>
 */
@Service
public class PracticeQuestionService {

    /**
     * Upper bound of candidates returned per task type. The practice picker shuffles them in Java, so a pool larger
     * than this is biased toward the lowest public ids (accepted for now, see plan 261024-hung-practice-timed-modes).
     */
    public static final int PRACTICE_CANDIDATE_CAP = 500;

    private final QuestionRepository questionRepository;

    public PracticeQuestionService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PracticeQuestionContent> findPublishedQuestion(String taskTypeKey) {
        if (taskTypeKey == null || taskTypeKey.isBlank()) {
            return Optional.empty();
        }
        String normalizedKey = TaskTypeCodeCompatibility.normalizeTaskTypeKey(taskTypeKey);
        return questionRepository.findPublishedPracticeQuestionIdsByTaskTypeKey(
                        normalizedKey, PageRequest.of(0, 1)).stream()
                .findFirst()
                .flatMap(questionRepository::findWithOptionsByPublicId)
                .map(this::toContent);
    }

    /** Public ids of every practice-ready question of a task type (capped), in a stable order for the picker. */
    @Transactional(readOnly = true)
    public List<UUID> findPublishedCandidateIds(String taskTypeKey) {
        if (taskTypeKey == null || taskTypeKey.isBlank()) {
            return List.of();
        }
        String normalizedKey = TaskTypeCodeCompatibility.normalizeTaskTypeKey(taskTypeKey);
        return questionRepository.findPublishedPracticeQuestionIdsByTaskTypeKey(
                normalizedKey, PageRequest.of(0, PRACTICE_CANDIDATE_CAP));
    }

    /** Practice-ready question count per normalized task type key; types with none are absent from the map. */
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> countPublishedByTaskTypeKeys(Collection<String> taskTypeKeys) {
        if (taskTypeKeys == null || taskTypeKeys.isEmpty()) {
            return java.util.Map.of();
        }
        List<String> normalized = taskTypeKeys.stream().map(TaskTypeCodeCompatibility::normalizeTaskTypeKey).distinct().toList();
        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        for (Object[] row : questionRepository.countPublishedPracticeQuestionsByTaskTypeKeys(normalized)) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }
        return java.util.Map.copyOf(counts);
    }

    /**
     * Loads the content and evaluation material of many questions in one query. Questions that are no longer
     * practice-ready are left out, so the caller can detect a pool that changed between picking and pinning.
     */
    @Transactional(readOnly = true)
    public List<PracticeQuestionPin> findPublishedQuestionsForPinning(Collection<UUID> publicIds) {
        if (publicIds == null || publicIds.isEmpty()) {
            return List.of();
        }
        return questionRepository.findWithOptionsByPublicIdIn(publicIds).stream()
                .filter(this::isPracticeReady)
                .map(question -> new PracticeQuestionPin(toContent(question), toEvaluation(question)))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean hasPublishedQuestion(String taskTypeKey) {
        if (taskTypeKey == null || taskTypeKey.isBlank()) {
            return false;
        }
        String normalizedKey = TaskTypeCodeCompatibility.normalizeTaskTypeKey(taskTypeKey);
        return questionRepository.existsByDeletedFalseAndVisibilityAndStatusAndCurrentTrueAndPoolAndTaskTypeKey(
                Visibility.SHARED, QuestionStatus.APPROVED, QuestionPool.PRACTICE, normalizedKey);
    }

    /**
     * Returns restricted evaluation material for a question already pinned to a
     * practice session. Controllers must never expose its answer-key fields.
     */
    @Transactional(readOnly = true)
    public Optional<PracticeQuestionEvaluationContent> findPublishedQuestionForEvaluation(UUID publicId) {
        return questionRepository.findWithOptionsByPublicId(publicId)
                .filter(this::isPracticeReady)
                .map(this::toEvaluation);
    }

    private boolean isPracticeReady(Question question) {
        return !question.isDeleted()
                && question.getVisibility() == Visibility.SHARED
                && question.getStatus() == QuestionStatus.APPROVED
                && question.isCurrent()
                && (question.isAvailableForPractice() || !question.isAvailabilityFlagsExplicit()
                        && question.getPool() == QuestionPool.PRACTICE);
    }

    private PracticeQuestionEvaluationContent toEvaluation(Question question) {
        return new PracticeQuestionEvaluationContent(question.getPublicId(),
                question.getReferenceAnswerText(), question.getCorrectAnswerText(),
                question.getOptions().stream()
                        .map(option -> new QuestionFreezeView.Option(option.getText(), option.isCorrect(),
                                option.getOrderIndex(), option.getBlankIndex(), option.getCorrectGapIndex()))
                        .toList(), question.getAudioPromptRef(), question.getImagePromptRef());
    }

    private PracticeQuestionContent toContent(Question question) {
        return new PracticeQuestionContent(
                question.getPublicId(),
                question.getTaskTypeKey(),
                question.getPromptText(),
                question.getMinWordCount(),
                question.getMaxWordCount(),
                question.getAudioPromptRef(),
                question.getImagePromptRef(),
                question.getOptions().stream()
                        .map(option -> new PracticeQuestionContent.Option(
                                option.getPublicId(), option.getText(), option.getOrderIndex(), option.getBlankIndex()))
                        .toList());
    }
}
