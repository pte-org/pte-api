package com.pte.scoring.service;

import com.pte.scoring.constant.ScoringConstants;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.exception.UnsupportedTaskTypeException;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Rule-based scoring for objective task types: {@code MC_READING_SINGLE},
 * {@code MC_READING_MULTIPLE}, {@code RE_ORDER_PARAGRAPHS},
 * {@code FILL_BLANKS_READING}, {@code FILL_BLANKS_READING_WRITING} — the 5
 * PTE Reading types with no AI-vendor scoring need. {@link #supports} lets
 * consumers skip unsupported types WITHOUT treating it as an error (an
 * unsupported type just stays PENDING, phase-07 design constraint);
 * {@link #score} fails fast if called for a type it can't grade.
 */
@Service
public class ObjectiveScoringService {

    private static final Set<String> SUPPORTED_TASK_TYPES = Set.of(
            ScoringConstants.TASK_TYPE_MC_READING_SINGLE,
            ScoringConstants.TASK_TYPE_MC_READING_MULTIPLE,
            ScoringConstants.TASK_TYPE_RE_ORDER_PARAGRAPHS,
            ScoringConstants.TASK_TYPE_FILL_BLANKS_READING,
            ScoringConstants.TASK_TYPE_FILL_BLANKS_READING_WRITING);

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
        return switch (answer.getTaskType()) {
            case ScoringConstants.TASK_TYPE_MC_READING_SINGLE -> scoreSingleChoice(answer);
            case ScoringConstants.TASK_TYPE_MC_READING_MULTIPLE -> scoreMultipleChoice(answer);
            case ScoringConstants.TASK_TYPE_RE_ORDER_PARAGRAPHS -> scoreReorderParagraphs(answer);
            case ScoringConstants.TASK_TYPE_FILL_BLANKS_READING, ScoringConstants.TASK_TYPE_FILL_BLANKS_READING_WRITING ->
                    scoreFillBlanks(answer);
            default -> throw new UnsupportedTaskTypeException();
        };
    }

    private int scoreSingleChoice(ScoringAnswer answer) {
        Integer correctOrderIndex = parseOptions(answer.getOptionsJson()).stream()
                .filter(FrozenOption::correct)
                .map(FrozenOption::orderIndex)
                .findFirst()
                .orElse(null);
        Integer submittedOrderIndex = parsePayloadAsOrderIndex(answer.getPayload());
        boolean correct = correctOrderIndex != null && correctOrderIndex.equals(submittedOrderIndex);
        return correct ? 100 : 0;
    }

    /**
     * PTE-standard negative marking: +1 per correct selection, -1 per
     * incorrect selection, floored at 0 for the question — never negative.
     * Converted to the shared 0–100 scale relative to the total number of
     * correct options (the maximum a student could earn).
     */
    private int scoreMultipleChoice(ScoringAnswer answer) {
        Set<Integer> correctIndexes = parseOptions(answer.getOptionsJson()).stream()
                .filter(FrozenOption::correct)
                .map(FrozenOption::orderIndex)
                .collect(java.util.stream.Collectors.toSet());
        if (correctIndexes.isEmpty()) {
            return 0;
        }
        Set<Integer> submitted = parsePayloadAsOrderIndexSet(answer.getPayload());
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
    private int scoreReorderParagraphs(ScoringAnswer answer) {
        int totalParagraphs = parseOptions(answer.getOptionsJson()).size();
        if (totalParagraphs <= 1) {
            return 100;
        }
        List<Integer> submitted = parsePayloadAsOrderIndexList(answer.getPayload());
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
     * Shared evaluator for both fill-blanks types: {@code FILL_BLANKS_READING}
     * (shared word bank — each correct option's target gap comes from {@link
     * FrozenOption#correctGapIndex}) and {@code FILL_BLANKS_READING_WRITING}
     * (per-blank groups — each correct option's target gap comes from {@link
     * FrozenOption#blankIndex}, since every option in a group already carries
     * it). Payload parsing preserves a trailing empty entry (mirrors the
     * frontend's positional convention) — the HIGH-risk case this plan's
     * frontend Phase 5 and this phase both call out explicitly.
     */
    private int scoreFillBlanks(ScoringAnswer answer) {
        Map<Integer, Integer> correctOrderIndexByGap = correctOrderIndexByGap(parseOptions(answer.getOptionsJson()));
        if (correctOrderIndexByGap.isEmpty()) {
            return 0;
        }
        List<Integer> submitted = parsePayloadAsOrderIndexList(answer.getPayload());
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

    /** Comma-joined orderIndex set (`MC_READING_MULTIPLE`) — unparsable entries are ignored, never thrown. */
    private Set<Integer> parsePayloadAsOrderIndexSet(String payload) {
        if (payload == null || payload.isBlank()) {
            return Set.of();
        }
        Set<Integer> result = new HashSet<>();
        for (String part : payload.split(",", -1)) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                result.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {
                // Malformed entry — skip it rather than fail the whole score.
            }
        }
        return result;
    }

    /**
     * Positional orderIndex list (`RE_ORDER_PARAGRAPHS`/fill-blanks) — an
     * empty or unparsable entry becomes {@code null} at that position, never
     * dropped. {@code split(",", -1)} (explicit limit) is required here: Java's
     * default {@code split(",")} silently drops trailing empty strings, which
     * would misparse a payload like {@code "2,0,"} as 2 positions instead of
     * 3 and misalign every gap index after the first empty one.
     */
    private List<Integer> parsePayloadAsOrderIndexList(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        for (String part : payload.split(",", -1)) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                result.add(null);
                continue;
            }
            try {
                result.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ex) {
                result.add(null);
            }
        }
        return result;
    }

    /**
     * Mirrors authoring's frozen option shape (text/correct/orderIndex/
     * blankIndex/correctGapIndex) — see {@code SnapshotPublishService.FrozenOption}'s
     * doc comment for why this must stay field-for-field identical while
     * exam-delivery's own copy doesn't need to.
     */
    private record FrozenOption(String text, boolean correct, int orderIndex, Integer blankIndex, Integer correctGapIndex) {
    }
}
