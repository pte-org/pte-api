package com.pte.scoring.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.scoring.constant.ScoringConstants;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.exception.UnsupportedTaskTypeException;
import org.springframework.stereotype.Service;

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

    private final ObjectMapper objectMapper;

    public ObjectiveScoringService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean supports(String taskType) {
        return SUPPORTED_TASK_TYPES.contains(taskType);
    }

    /** @return raw unscaled score: 1 correct, 0 incorrect. Phase 8 owns the 10–90 conversion. */
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
        return correct ? 1 : 0;
    }

    private Integer correctOptionOrderIndex(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return null;
        }
        try {
            List<FrozenOption> options = objectMapper.readValue(optionsJson, new TypeReference<List<FrozenOption>>() {
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
