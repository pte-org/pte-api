package com.pte.scoring.internal.rubric.dto;

import com.pte.scoring.rubric.ScoreTrace;
import java.util.UUID;

public record RubricSimulationResponse(UUID rubricVersionPublicId, int rawScore, Integer formBand,
                                      ScoreTrace scoreTrace) {
}
