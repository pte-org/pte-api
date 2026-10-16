package com.pte.itembank;

import com.pte.itembank.domain.Question;
import com.pte.itembank.dto.response.PracticeQuestionContent;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.domain.enums.Visibility;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Public itembank facade for the standalone practice module.
 *
 * <p>Only APPROVED, current, shared questions from the immutable PRACTICE
 * pool are exposed, and the projection deliberately removes scoring keys.</p>
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

    private PracticeQuestionContent toContent(Question question) {
        return new PracticeQuestionContent(
                question.getPublicId(),
                question.getTaskTypeKey(),
                question.getPromptText(),
                question.getMinWordCount(),
                question.getMaxWordCount(),
                question.getOptions().stream()
                        .map(option -> new PracticeQuestionContent.Option(
                                option.getText(), option.getOrderIndex(), option.getBlankIndex()))
                        .toList());
    }
}
