package com.aptis.modules.scoring.interfaces;

import com.aptis.modules.scoring.dto.ScoreResponse;

public interface ScoringOperations {

    ScoreResponse getScore(Long attemptId);
}
