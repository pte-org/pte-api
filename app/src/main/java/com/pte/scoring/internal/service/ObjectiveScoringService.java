package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.exception.UnsupportedTaskTypeException;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Rule-based scoring for objective task types: the 5 PTE Reading types plus
 * the four option-based Listening types ({@code MC_LISTENING_SINGLE}, {@code
 * MC_LISTENING_MULTIPLE}, {@code HIGHLIGHT_CORRECT_SUMMARY}, and {@code
 * SELECT_MISSING_WORD}), plus the reference-based {@code
 * FILL_BLANKS_LISTENING}, {@code HIGHLIGHT_INCORRECT_WORDS}, and {@code
 * WRITE_FROM_DICTATION} types. {@link #supports} lets consumers skip unsupported
 * types WITHOUT treating it as an error (an unsupported type just stays
 * PENDING); {@link #score} fails fast if called for a type it can't grade.
 */
@Service
public class ObjectiveScoringService {

    private static final Set<String> SUPPORTED_TASK_TYPES = Set.of(
            ScoringConstants.TASK_TYPE_MC_READING_SINGLE,
            ScoringConstants.TASK_TYPE_MC_READING_MULTIPLE,
            ScoringConstants.TASK_TYPE_RE_ORDER_PARAGRAPHS,
            ScoringConstants.TASK_TYPE_FILL_BLANKS_READING,
            ScoringConstants.TASK_TYPE_FILL_BLANKS_READING_WRITING,
            ScoringConstants.TASK_TYPE_MC_LISTENING_SINGLE,
            ScoringConstants.TASK_TYPE_MC_LISTENING_MULTIPLE,
            ScoringConstants.TASK_TYPE_HIGHLIGHT_CORRECT_SUMMARY,
            ScoringConstants.TASK_TYPE_SELECT_MISSING_WORD,
            ScoringConstants.TASK_TYPE_FILL_BLANKS_LISTENING,
            ScoringConstants.TASK_TYPE_HIGHLIGHT_INCORRECT_WORDS,
            ScoringConstants.TASK_TYPE_WRITE_FROM_DICTATION);

    private final JsonMapper jsonMapper;

    public ObjectiveScoringService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public boolean supports(String taskType) {
        return taskType != null && SUPPORTED_TASK_TYPES.contains(taskType);
    }

    /**
     * @return raw score on a 0–100 PERCENTAGE scale (100 correct, 0 incorrect)
     * — NOT 0/1. AI scoring (also 0–100) must combine with objective scores in
     * reporting's aggregation formula (percentCorrect = average of
     * contributing rawScores / 100), so every scorer must share one scale.
     */
    public int score(ScoringAnswer answer) {
        return switch (answer.getTaskType()) {
            case ScoringConstants.TASK_TYPE_MC_READING_SINGLE -> scoreSingleChoice(answer);
            case ScoringConstants.TASK_TYPE_MC_READING_MULTIPLE -> scoreMultipleChoice(answer);
            case ScoringConstants.TASK_TYPE_MC_LISTENING_SINGLE,
                    ScoringConstants.TASK_TYPE_HIGHLIGHT_CORRECT_SUMMARY,
                    ScoringConstants.TASK_TYPE_SELECT_MISSING_WORD -> scoreSingleChoice(answer);
            case ScoringConstants.TASK_TYPE_MC_LISTENING_MULTIPLE -> scoreMultipleChoice(answer);
            case ScoringConstants.TASK_TYPE_RE_ORDER_PARAGRAPHS -> scoreReorderParagraphs(answer);
            case ScoringConstants.TASK_TYPE_FILL_BLANKS_READING, ScoringConstants.TASK_TYPE_FILL_BLANKS_READING_WRITING ->
                    scoreFillBlanks(answer);
            case ScoringConstants.TASK_TYPE_FILL_BLANKS_LISTENING -> scoreListeningFillBlanks(answer);
            case ScoringConstants.TASK_TYPE_HIGHLIGHT_INCORRECT_WORDS -> scoreHighlightIncorrectWords(answer);
            case ScoringConstants.TASK_TYPE_WRITE_FROM_DICTATION -> scoreWriteFromDictation(answer);
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
        if (totalParagraphs == 0) {
            return 0;
        }
        if (totalParagraphs == 1) {
            List<Integer> submitted = parsePayloadAsOrderIndexList(answer.getPayload());
            return submitted.size() == 1 && submitted.get(0) != null ? 100 : 0;
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
     * frontend's positional convention).
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

    /**
     * Listening fill-blanks use a positional JSON string array in
     * {@code correctAnswerText}; the submitted payload is the same positions
     * joined with commas. A malformed reference is unscorable and therefore
     * fails closed at zero instead of accidentally awarding a perfect score.
     */
    private int scoreListeningFillBlanks(ScoringAnswer answer) {
        List<String> expected = parseListeningFillBlankReference(answer.getCorrectAnswerText());
        if (expected.isEmpty()) {
            return 0;
        }
        List<String> submitted = parseListeningFillBlankPayload(answer.getPayload());
        int correctGaps = 0;
        for (int i = 0; i < expected.size(); i++) {
            if (i < submitted.size() && expected.get(i).equals(submitted.get(i))) {
                correctGaps++;
            }
        }
        return Math.round(100f * correctGaps / expected.size());
    }

    /**
     * Highlight-incorrect-words uses zero-based transcript token positions.
     * Pearson's rule is +1 for each expected selection and -1 for each
     * unexpected selection, floored at zero for the item.
     */
    private int scoreHighlightIncorrectWords(ScoringAnswer answer) {
        List<Integer> expected = parseHighlightIncorrectReference(answer.getCorrectAnswerText());
        if (expected.isEmpty()) {
            return 0;
        }
        Set<Integer> expectedIndexes = new HashSet<>(expected);
        Set<Integer> submitted = parsePayloadAsOrderIndexSet(answer.getPayload());
        int correctSelections = 0;
        int incorrectSelections = 0;
        for (Integer selected : submitted) {
            if (expectedIndexes.contains(selected)) {
                correctSelections++;
            } else {
                incorrectSelections++;
            }
        }
        int points = Math.max(correctSelections - incorrectSelections, 0);
        return Math.round(100f * points / expectedIndexes.size());
    }

    /**
     * Write-from-dictation awards partial credit by the number of reference
     * words preserved in order. Comparison is case-insensitive and removes
     * punctuation at token boundaries, while the LCS prevents reordered or duplicated words from
     * being counted as correct positions.
     */
    private int scoreWriteFromDictation(ScoringAnswer answer) {
        List<String> expected = tokenizeDictation(answer.getCorrectAnswerText());
        if (expected.isEmpty()) {
            return 0;
        }
        List<String> submitted = tokenizeDictation(answer.getPayload());
        int matchedWords = longestCommonSubsequenceLength(expected, submitted);
        return Math.round(100f * matchedWords / expected.size());
    }

    private List<String> parseListeningFillBlankReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = jsonMapper.readValue(reference, new TypeReference<List<String>>() {
            });
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            for (String value : values) {
                if (value == null || value.isBlank() || value.contains(",")) {
                    return List.of();
                }
            }
            return List.copyOf(values);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Integer> parseHighlightIncorrectReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return List.of();
        }
        try {
            List<Integer> values = jsonMapper.readValue(reference, new TypeReference<List<Integer>>() {
            });
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            Set<Integer> seen = new HashSet<>();
            for (Integer value : values) {
                if (value == null || value < 0 || !seen.add(value)) {
                    return List.of();
                }
            }
            return List.copyOf(values);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> parseListeningFillBlankPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : payload.split(",", -1)) {
            result.add(normalizeGapValue(value));
        }
        return result;
    }

    private String normalizeGapValue(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private List<String> tokenizeDictation(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String rawToken : value.toLowerCase(Locale.ROOT).split("\\s+")) {
            String token = rawToken.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "");
            if (!token.isEmpty()) {
                result.add(token);
            }
        }
        return result;
    }

    private int longestCommonSubsequenceLength(List<String> expected, List<String> submitted) {
        int[] previous = new int[submitted.size() + 1];
        for (String expectedWord : expected) {
            int[] current = new int[submitted.size() + 1];
            for (int j = 1; j <= submitted.size(); j++) {
                if (expectedWord.equals(submitted.get(j - 1))) {
                    current[j] = previous[j - 1] + 1;
                } else {
                    current[j] = Math.max(previous[j], current[j - 1]);
                }
            }
            previous = current;
        }
        return previous[submitted.size()];
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
     * blankIndex/correctGapIndex).
     */
    private record FrozenOption(String text, boolean correct, int orderIndex, Integer blankIndex, Integer correctGapIndex) {
    }
}
