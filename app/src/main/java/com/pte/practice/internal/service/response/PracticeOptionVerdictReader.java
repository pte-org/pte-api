package com.pte.practice.internal.service.response;

import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.dto.response.PracticeItemResultResponse.OptionVerdict;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Marks the options of an answer scored against a pinned answer key right or wrong, so the student can see which of
 * their picks landed. Only the answers whose key is a list of options are read: the choice types, where the picks are
 * option positions, and the fill-in-the-blanks types, where each gap holds the text of the word put in it. Anything
 * else, an unreadable answer or key, and a key with no correct option at all give no verdicts, so the screen falls
 * back to showing the score alone rather than marking something wrong by mistake.
 */
public final class PracticeOptionVerdictReader {

    private static final String SELECTED_OPTION = "selectedOption";
    private static final String SELECTED_OPTIONS = "selectedOptions";
    private static final String PLACEMENTS = "placements";
    private static final String SELECTIONS = "selections";
    private static final String ORDER_INDEX = "orderIndex";
    private static final String BLANK_INDEX = "blankIndex";
    private static final String CORRECT = "correct";
    private static final String TEXT = "text";
    private static final Set<String> SINGLE_CHOICE_RENDERERS = Set.of("MC_READING_SINGLE_V1", "MC_LISTENING_SINGLE_V1",
            "SELECT_MISSING_WORD_V1", "HIGHLIGHT_CORRECT_SUMMARY_V1");
    private static final Set<String> MULTIPLE_CHOICE_RENDERERS = Set.of("MC_READING_MULTIPLE_V1",
            "MC_LISTENING_MULTIPLE_V1");

    private PracticeOptionVerdictReader() {
    }

    public static List<OptionVerdict> read(JsonMapper jsonMapper, PracticeResponse item) {
        String rendererKey = item.getRendererKey();
        if (rendererKey == null || item.getSavedPayload() == null || item.getObjectiveOptionsJson() == null) {
            return List.of();
        }
        JsonNode payload;
        JsonNode key;
        try {
            payload = jsonMapper.readTree(item.getSavedPayload());
            key = jsonMapper.readTree(item.getObjectiveOptionsJson());
        } catch (JacksonException ex) {
            return List.of();
        }
        if (payload == null || !payload.isObject() || key == null || !key.isArray()) {
            return List.of();
        }
        List<OptionVerdict> verdicts = verdictsOf(rendererKey, payload, key);
        if (verdicts.stream().noneMatch(OptionVerdict::correct)) {
            return List.of();
        }
        verdicts.sort(Comparator.comparingInt(OptionVerdict::orderIndex));
        return verdicts;
    }

    private static List<OptionVerdict> verdictsOf(String rendererKey, JsonNode payload, JsonNode key) {
        if (SINGLE_CHOICE_RENDERERS.contains(rendererKey)) {
            return choiceVerdicts(key, payload.path(SELECTED_OPTION));
        }
        if (MULTIPLE_CHOICE_RENDERERS.contains(rendererKey)) {
            return choiceVerdicts(key, payload.path(SELECTED_OPTIONS));
        }
        if ("FILL_IN_THE_BLANKS_DROPDOWN_V1".equals(rendererKey)) {
            return blankVerdicts(key, payload.path(SELECTIONS));
        }
        return "FILL_IN_THE_BLANKS_DRAG_AND_DROP_V1".equals(rendererKey)
                ? blankVerdicts(key, payload.path(PLACEMENTS)) : new ArrayList<>();
    }

    /** A choice answer names the options it picked by their position, as a single id or a list of them. */
    private static List<OptionVerdict> choiceVerdicts(JsonNode key, JsonNode picked) {
        Set<Integer> indexes = pickedIndexes(picked);
        List<OptionVerdict> verdicts = new ArrayList<>();
        for (JsonNode option : key) {
            int orderIndex = option.path(ORDER_INDEX).asInt(-1);
            if (orderIndex >= 0) {
                verdicts.add(new OptionVerdict(orderIndex, indexes.contains(orderIndex),
                        option.path(CORRECT).asBoolean(false), text(option)));
            }
        }
        return verdicts;
    }

    /** A fill-in-the-blanks answer holds, per gap, the text of the word put in it; each option belongs to one gap. */
    private static List<OptionVerdict> blankVerdicts(JsonNode key, JsonNode byGap) {
        List<OptionVerdict> verdicts = new ArrayList<>();
        for (JsonNode option : key) {
            int orderIndex = option.path(ORDER_INDEX).asInt(-1);
            JsonNode blankIndex = option.path(BLANK_INDEX);
            if (orderIndex < 0 || !blankIndex.isInt()) {
                continue;
            }
            String word = text(option);
            String inGap = byGap.path(String.valueOf(blankIndex.asInt())).asText("").strip();
            verdicts.add(new OptionVerdict(orderIndex, !word.isEmpty() && word.equals(inGap),
                    option.path(CORRECT).asBoolean(false), blankIndex.asInt(), word));
        }
        return verdicts;
    }

    private static String text(JsonNode option) {
        return option.path(TEXT).asText("").strip();
    }

    private static Set<Integer> pickedIndexes(JsonNode picked) {
        Set<Integer> indexes = new HashSet<>();
        if (picked.isArray()) {
            for (JsonNode id : picked) {
                addIndex(indexes, id);
            }
        } else {
            addIndex(indexes, picked);
        }
        return indexes;
    }

    private static void addIndex(Set<Integer> indexes, JsonNode id) {
        try {
            indexes.add(Integer.parseInt(id.asText("").strip()));
        } catch (NumberFormatException ex) {
            // An id that is not an option position matches no option; the rest of the answer still counts.
        }
    }
}
