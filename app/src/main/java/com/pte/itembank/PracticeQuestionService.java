package com.pte.itembank;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.domain.enums.Visibility;
import com.pte.itembank.dto.response.PracticeQuestionContent;
import com.pte.itembank.dto.response.PracticeQuestionEvaluationContent;
import com.pte.itembank.internal.repository.QuestionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .filter(question -> !question.isDeleted()
                        && question.getVisibility() == Visibility.SHARED
                        && question.getStatus() == QuestionStatus.APPROVED
                        && question.isCurrent()
                        && (question.isAvailableForPractice() || !question.isAvailabilityFlagsExplicit()
                                && question.getPool() == QuestionPool.PRACTICE))
                .map(question -> new PracticeQuestionEvaluationContent(question.getPublicId(),
                        question.getReferenceAnswerText(), question.getCorrectAnswerText(),
                        question.getOptions().stream()
                                .map(option -> new PracticeQuestionContent.Option(option.getPublicId(),
                                        option.getText(), option.getOrderIndex(), option.getBlankIndex()))
                                .toList(), question.getAudioPromptRef(), question.getImagePromptRef()));
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
