package com.pte.scoring.service;

import com.pte.scoring.constant.ScoringConstants;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.exception.UnsupportedTaskTypeException;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

/**
 * Rule-based scoring for objective task types. Milestone 1 covers
 * {@code MC_READING_SINGLE} only — speaking/writing need Phase 9's AI vendor.
 * {@link #supports} lets consumers skip unsupported types WITHOUT treating it
 * as an error (an unsupported type just stays PENDING, phase-07 design
 * constraint); {@link #score} fails fast if called for a type it can't grade.
 */
@Service
public class ObjectiveScoringService {

    private static final Set<String> SUPPORTED_TASK_TYPES = Set.of(ScoringConstants.TASK_TYPE_MC_READING_SINGLE);

    private final JsonMapper jsonMapper;

    public ObjectiveScoringService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public boolean supports(String taskType) {
        return SUPPORTED_TASK_TYPES.contains(taskType);
    }

    /**
     * @return raw score on a 0–100 PERCENTAGE scale (100 correct, 0 incorrect)
     * — NOT 0/1. Phase 9 correction: AI scoring (also 0–100) must combine with
     * objective scores in reporting's aggregation formula (percentCorrect =
     * average of contributing rawScores / 100), so every scorer must share one
     * scale. Phase 8 owns the 10–90 conversion on top of that average.
     */
    public int score(ScoringAnswer answer) {
        if (ScoringConstants.TASK_TYPE_MC_READING_SINGLE.equals(answer.getTaskType())) {
            return scoreSingleChoice(answer);
        }
        throw new UnsupportedTaskTypeException();
    }

    private int scoreSingleChoice(ScoringAnswer answer) {
        Integer correctOrderIndex = correctOptionOrderIndex(answer.getOptionsJson());
        Integer submittedOrderIndex = parsePayloadAsOrderIndex(answer.getPayload());
        boolean correct = correctOrderIndex != null && correctOrderIndex.equals(submittedOrderIndex);
        return correct ? 100 : 0;
    }

    private Integer correctOptionOrderIndex(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return null;
        }
        try {
            List<FrozenOption> options = jsonMapper.readValue(optionsJson, new TypeReference<List<FrozenOption>>() {
            });
            return options.stream().filter(FrozenOption::correct).map(FrozenOption::orderIndex).findFirst().orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    /** A missing/malformed/expired-null submission scores incorrect, never throws. */
    private Integer parsePayloadAsOrderIndex(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(payload.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Mirrors authoring/exam-delivery's frozen option shape (text/correct/orderIndex). */
    private record FrozenOption(String text, boolean correct, int orderIndex) {
    }
}
