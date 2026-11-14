package com.pte.practice.internal.service;

import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Converts the JSON a practice renderer stores into the payload the exam scorers read:
 * an option index for single choice, comma-joined indexes or positions for the list types,
 * raw text for written answers and the media id for recordings. It fails closed (empty)
 * on anything it cannot convert unambiguously, so a bad answer is never scored as a zero.
 */
@Component
public class PracticeAnswerCanonicalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final JsonMapper jsonMapper;

    public PracticeAnswerCanonicalizer(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public Optional<String> canonicalize(String rendererKey, String savedPayload, String objectiveOptionsJson) {
        return canonicalize(rendererKey, savedPayload, objectiveOptionsJson, null);
    }

    /** {@code studentOptionsJson} is the pinned student view, needed to map shuffled Re-order labels back. */
    public Optional<String> canonicalize(String rendererKey, String savedPayload, String objectiveOptionsJson,
            String studentOptionsJson) {
        if (rendererKey == null || savedPayload == null || savedPayload.isBlank()) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = jsonMapper.readTree(savedPayload);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (root == null || !root.isObject()) {
            return Optional.empty();
        }
        String value = switch (rendererKey) {
            case "MC_READING_SINGLE_V1", "MC_LISTENING_SINGLE_V1", "SELECT_MISSING_WORD_V1",
                    "HIGHLIGHT_CORRECT_SUMMARY_V1" -> text(root, "selectedOption");
            case "MC_READING_MULTIPLE_V1", "MC_LISTENING_MULTIPLE_V1" -> join(root.path("selectedOptions"), false);
            case "RE_ORDER_PARAGRAPHS_V1" -> reorderOriginalIndexes(root.path("order"), objectiveOptionsJson,
                    studentOptionsJson);
            case "FILL_IN_THE_BLANKS_DRAG_AND_DROP_V1" ->
                    joinBlankSelections(root.path("placements"), objectiveOptionsJson, false);
            case "FILL_IN_THE_BLANKS_DROPDOWN_V1" ->
                    joinBlankSelections(root.path("selections"), objectiveOptionsJson, true);
            case "SUMMARIZE_WRITTEN_TEXT_V1", "WRITE_ESSAY_V1", "SUMMARIZE_SPOKEN_TEXT_V1",
                    "WRITE_FROM_DICTATION_V1" -> text(root, "text");
            case "FILL_IN_THE_BLANKS_TYPE_IN_V1" -> join(root.path("blanks"), true);
            case "HIGHLIGHT_INCORRECT_WORDS_V1" -> join(root.path("selectedWordIndexes"), false);
            case "READ_ALOUD_V1", "REPEAT_SENTENCE_V1", "DESCRIBE_IMAGE_V1", "RE_TELL_LECTURE_V1",
                    "ANSWER_SHORT_QUESTION_V1", "RESPOND_TO_A_SITUATION_V1", "SUMMARIZE_GROUP_DISCUSSION_V1" ->
                    mediaId(root);
            default -> null;
        };
        return Optional.ofNullable(value);
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    private String mediaId(JsonNode root) {
        try {
            return UUID.fromString(root.path("mediaPublicId").asText()).toString();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Joins an array with commas; {@code allowEmpty} keeps blank entries as positions (type-in blanks). */
    private String join(JsonNode node, boolean allowEmpty) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode entry : node) {
            String value = entry.asText("");
            if (value.contains(",") || (!allowEmpty && value.isBlank())) {
                return null;
            }
            values.add(value);
        }
        return String.join(",", values);
    }

    /**
     * Converts the renderer's word values into the stable option indexes the deterministic scorer uses.
     * Blank positions stay positional, so an unanswered middle or trailing blank is an empty entry.
     * Duplicate option text would make the answer ambiguous, so it fails closed.
     */
    private String joinBlankSelections(JsonNode selections, String optionsJson, boolean groupedByBlank) {
        if (selections == null || !selections.isObject() || optionsJson == null || optionsJson.isBlank()) {
            return null;
        }
        List<Option> options;
        try {
            options = jsonMapper.readValue(optionsJson, new TypeReference<List<Option>>() { });
        } catch (RuntimeException exception) {
            return null;
        }
        int blankCount = options.stream()
                .mapToInt(option -> groupedByBlank ? valueOrMinusOne(option.blankIndex())
                        : valueOrMinusOne(option.correctGapIndex()))
                .max()
                .orElse(-1) + 1;
        if (blankCount <= 0) {
            return null;
        }
        List<String> orderIndexes = new ArrayList<>(blankCount);
        for (int blankIndex = 0; blankIndex < blankCount; blankIndex++) {
            final int gapIndex = blankIndex;
            String selectedText = selections.path(String.valueOf(gapIndex)).asText("");
            if (selectedText.isBlank()) {
                orderIndexes.add("");
                continue;
            }
            List<Option> matches = options.stream()
                    .filter(option -> sameWord(option.text(), selectedText))
                    .filter(option -> !groupedByBlank || Integer.valueOf(gapIndex).equals(option.blankIndex()))
                    .toList();
            if (matches.size() != 1) {
                return null;
            }
            orderIndexes.add(String.valueOf(matches.get(0).orderIndex()));
        }
        return String.join(",", orderIndexes);
    }

    /**
     * The learner's labels back to the original paragraph indexes. A label is the position shown to the student;
     * both views list the paragraph text, so text is the join key. Without a student view (or for items pinned before
     * labels were shuffled) labels already are original indexes. Duplicate paragraph text fails closed.
     */
    private String reorderOriginalIndexes(JsonNode order, String objectiveOptionsJson, String studentOptionsJson) {
        String labels = join(order, false);
        if (labels == null || studentOptionsJson == null || studentOptionsJson.isBlank()
                || objectiveOptionsJson == null || objectiveOptionsJson.isBlank()) {
            return labels;
        }
        List<Option> answerKey;
        List<ShownOption> shown;
        try {
            answerKey = jsonMapper.readValue(objectiveOptionsJson, new TypeReference<List<Option>>() { });
            shown = jsonMapper.readValue(studentOptionsJson, new TypeReference<List<ShownOption>>() { });
        } catch (RuntimeException exception) {
            return labels;
        }
        List<String> original = new ArrayList<>();
        for (String label : labels.split(",")) {
            List<ShownOption> shownMatches = shown.stream()
                    .filter(option -> String.valueOf(option.orderIndex()).equals(label)).toList();
            if (shownMatches.size() != 1) {
                return null;
            }
            List<Option> keyMatches = answerKey.stream()
                    .filter(option -> sameWord(option.text(), shownMatches.get(0).text())).toList();
            if (keyMatches.size() != 1) {
                return null;
            }
            original.add(String.valueOf(keyMatches.get(0).orderIndex()));
        }
        return String.join(",", original);
    }

    private boolean sameWord(String left, String right) {
        return left != null && right != null && normalize(left).equals(normalize(right));
    }

    private String normalize(String value) {
        return WHITESPACE.matcher(value.trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    private int valueOrMinusOne(Integer value) {
        return value == null ? -1 : value;
    }

    /** The student view of a paragraph: only its text and the label shown to the learner. */
    private record ShownOption(String text, int orderIndex) {
    }

    private record Option(String text, boolean correct, int orderIndex, Integer blankIndex, Integer correctGapIndex) {
    }
}
