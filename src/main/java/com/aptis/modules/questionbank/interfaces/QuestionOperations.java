package com.aptis.modules.questionbank.interfaces;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.questionbank.domain.DifficultyLevel;
import com.aptis.modules.questionbank.domain.QuestionStatus;
import com.aptis.modules.questionbank.domain.QuestionType;
import com.aptis.modules.questionbank.domain.Skill;
import com.aptis.modules.questionbank.dto.request.CreateQuestionRequest;
import com.aptis.modules.questionbank.dto.request.UpdateQuestionRequest;
import com.aptis.modules.questionbank.dto.response.QuestionResponse;

public interface QuestionOperations {

    QuestionResponse getQuestion(UUID id, JwtPrincipal principal);

    QuestionResponse createQuestion(CreateQuestionRequest request, JwtPrincipal principal);

    // createQuestionWithAsset removed in favor of two-step architecture

    QuestionResponse updateQuestion(UUID id, UpdateQuestionRequest request, JwtPrincipal principal);

    void deleteQuestion(UUID id, JwtPrincipal principal);

    QuestionResponse publishQuestion(UUID id, JwtPrincipal principal);

    QuestionResponse uploadAudio(UUID id, MultipartFile file, JwtPrincipal principal);

    Page<QuestionResponse> listQuestions(
            Skill skill,
            Integer part,
            QuestionType questionType,
            DifficultyLevel difficultyLevel,
            QuestionStatus status,
            Boolean isCurrent,
            JwtPrincipal principal,
            Pageable pageable);
}
