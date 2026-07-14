package com.aptis.modules.scoring.interfaces;

import java.util.List;

import com.aptis.modules.scoring.dto.request.GradeAnswerRequest;
import com.aptis.modules.scoring.dto.response.AnswerResponse;
import com.aptis.modules.scoring.dto.response.AttemptResponse;

public interface GraderScoringOperations {
    List<AttemptResponse> listAttempts(Long graderId);
    List<AnswerResponse> getAttemptAnswers(Long graderId, Long attemptId);
    void gradeAnswer(Long graderId, Long attemptId, Long answerId, GradeAnswerRequest request);
    List<Long> getScopeOrgs(Long graderId);
    void validateAttemptScope(Long graderId, Long attemptId);
}
