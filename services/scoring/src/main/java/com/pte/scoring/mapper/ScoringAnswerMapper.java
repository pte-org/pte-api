package com.pte.scoring.mapper;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.dto.response.ScoringAnswerResponse;

public final class ScoringAnswerMapper {

    private ScoringAnswerMapper() {
    }

    public static ScoringAnswerResponse toResponse(ScoringAnswer answer) {
        return new ScoringAnswerResponse(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTaskType(), answer.getStatus().name(), answer.getRawScore());
    }
}
