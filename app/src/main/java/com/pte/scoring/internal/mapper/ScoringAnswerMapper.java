package com.pte.scoring.internal.mapper;

import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.internal.dto.response.ScoringAnswerResponse;

public final class ScoringAnswerMapper {

    private ScoringAnswerMapper() {
    }

    public static ScoringAnswerResponse toResponse(AiEvaluation answer) {
        return new ScoringAnswerResponse(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTaskType(), answer.getStatus().name(), answer.getRawScore(), answer.getTeacherScore());
    }
}
