package com.pte.scoring.internal.service.objective;

import com.pte.attempt.dto.response.SubmittedAnswerView;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Scores option-based objective answers against the frozen option set (choice, re-order, fill-blanks). */
class OptionAnswerScorer {

    private final JsonMapper jsonMapper;

    OptionAnswerScorer(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    int scoreSingleChoice(SubmittedAnswerView answer) {
        Integer correctOrderIndex = parseOptions(answer.optionsJson()).stream()
                .filter(FrozenOption::correct)
                .map(FrozenOption::orderIndex)
                .findFirst()
                .orElse(null);
        Integer submittedOrderIndex = OrderIndexPayload.parsePayloadAsOrderIndex(answer.payload());
        boolean correct = correctOrderIndex != null && correctOrderIndex.equals(submittedOrderIndex);
        return correct ? 100 : 0;
    }

    /**
     * PTE-standard negative marking: +1 per correct selection, -1 per
     * incorrect selection, floored at 0 for the question — never negative.
     * Converted to the shared 0–100 scale relative to the total number of
     * correct options (the maximum a student could earn).
     */
    int scoreMultipleChoice(SubmittedAnswerView answer) {
        Set<Integer> correctIndexes = parseOptions(answer.optionsJson()).stream()
                .filter(FrozenOption::correct)
                .map(FrozenOption::orderIndex)
                .collect(java.util.stream.Collectors.toSet());
        if (correctIndexes.isEmpty()) {
            return 0;
        }
        Set<Integer> submitted = OrderIndexPayload.parsePayloadAsOrderIndexSet(answer.payload());
        int correctSelections = 0;
        int incorrectSelections = 0;
        for (Integer selected : submitted) {
            if (correctIndexes.contains(selected)) {
                correctSelections++;
            } else {
                incorrectSelections++;
            }
        }
        int points = Math.max(correctSelections - incorrectSelections, 0);
        return Math.round(100f * points / correctIndexes.size());
    }

    /**
     * PTE-standard partial credit: 1 point per correctly-adjacent pair in
     * the student's submitted sequence, not an all-or-nothing exact-match
     * check. Since {@code orderIndex} is each paragraph's stable
     * correct-position identity, a pair at submitted positions (i, i+1) is
     * correct exactly when {@code submitted[i+1] == submitted[i] + 1}.
     */
    int scoreReorderParagraphs(SubmittedAnswerView answer) {
        int totalParagraphs = parseOptions(answer.optionsJson()).size();
        if (totalParagraphs == 0) {
            return 0;
        }
        if (totalParagraphs == 1) {
            List<Integer> submitted = OrderIndexPayload.parsePayloadAsOrderIndexList(answer.payload());
            return submitted.size() == 1 && submitted.get(0) != null ? 100 : 0;
        }
        List<Integer> submitted = OrderIndexPayload.parsePayloadAsOrderIndexList(answer.payload());
        int totalPairs = totalParagraphs - 1;
        int correctPairs = 0;
        for (int i = 0; i < submitted.size() - 1; i++) {
            Integer current = submitted.get(i);
            Integer next = submitted.get(i + 1);
            if (current != null && next != null && next == current + 1) {
                correctPairs++;
            }
        }
        return Math.round(100f * correctPairs / totalPairs);
    }

    /**
     * Shared evaluator for both fill-blanks types: {@code FILL_IN_THE_BLANKS_DRAG_AND_DROP}
     * (shared word bank — each correct option's target gap comes from {@link
     * FrozenOption#correctGapIndex}) and {@code FILL_IN_THE_BLANKS_DROPDOWN}
     * (per-blank groups — each correct option's target gap comes from {@link
     * FrozenOption#blankIndex}, since every option in a group already carries
     * it). Payload parsing preserves a trailing empty entry (mirrors the
     * frontend's positional convention).
     */
    int scoreFillBlanks(SubmittedAnswerView answer) {
        Map<Integer, Integer> correctOrderIndexByGap = correctOrderIndexByGap(parseOptions(answer.optionsJson()));
        if (correctOrderIndexByGap.isEmpty()) {
            return 0;
        }
        List<Integer> submitted = OrderIndexPayload.parsePayloadAsOrderIndexList(answer.payload());
        int correctGaps = 0;
        for (Map.Entry<Integer, Integer> entry : correctOrderIndexByGap.entrySet()) {
            int gapIndex = entry.getKey();
            Integer submittedOrderIndex = gapIndex < submitted.size() ? submitted.get(gapIndex) : null;
            if (submittedOrderIndex != null && submittedOrderIndex.equals(entry.getValue())) {
                correctGaps++;
            }
        }
        return Math.round(100f * correctGaps / correctOrderIndexByGap.size());
    }

    /** gapIndex -> the correct option's orderIndex for that gap, ascending by gap. */
    private Map<Integer, Integer> correctOrderIndexByGap(List<FrozenOption> options) {
        Map<Integer, Integer> result = new TreeMap<>();
        for (FrozenOption option : options) {
            if (!option.correct()) {
                continue;
            }
            Integer gapIndex = option.blankIndex() != null ? option.blankIndex() : option.correctGapIndex();
            if (gapIndex != null) {
                result.put(gapIndex, option.orderIndex());
            }
        }
        return result;
    }

    private List<FrozenOption> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(optionsJson, new TypeReference<List<FrozenOption>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    /**
     * Mirrors authoring's frozen option shape (text/correct/orderIndex/
     * blankIndex/correctGapIndex).
     */
    private record FrozenOption(String text, boolean correct, int orderIndex, Integer blankIndex, Integer correctGapIndex) {
    }
}
