package com.aptis.modules.questionbank.interfaces;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.aptis.modules.questionbank.domain.DifficultyLevel;
import com.aptis.modules.questionbank.domain.QuestionStatus;
import com.aptis.modules.questionbank.domain.QuestionType;
import com.aptis.modules.questionbank.domain.Skill;
import com.aptis.modules.questionbank.dto.request.CreateQuestionRequest;
import com.aptis.modules.questionbank.dto.request.UpdateQuestionRequest;
import com.aptis.modules.questionbank.dto.response.QuestionResponse;

public interface QuestionOperations {

    QuestionResponse getQuestion(UUID id);

    QuestionResponse createQuestion(CreateQuestionRequest request, Long userId);

    // createQuestionWithAsset removed in favor of two-step architecture

    QuestionResponse updateQuestion(UUID id, UpdateQuestionRequest request, Long userId);

    void deleteQuestion(UUID id);

    Page<QuestionResponse> listQuestions(
            Skill skill,
            Integer part,
            QuestionType questionType,
            DifficultyLevel difficultyLevel,
            QuestionStatus status,
            Boolean isCurrent,
            Pageable pageable);
}
