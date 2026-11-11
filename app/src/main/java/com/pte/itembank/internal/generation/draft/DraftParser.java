package com.pte.itembank.internal.generation.draft;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.exception.GenerationRequestInvalidException;
import com.pte.itembank.internal.generation.draft.image.PictureDrafts;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Strict JSON parser for LLM responses. Returns an ItemDraft or throws
 * GenerationRequestInvalidException with code GENERATION_SCHEMA_INVALID.
 */
@Component
public class DraftParser {

    private final JsonMapper mapper;

    public DraftParser(JsonMapper mapper) {
        this.mapper = mapper;
    }

    /** The answer hints at the root of a generator response; both languages are required for every task type. */
    public AnswerHint parseHint(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            return new AnswerHint(requireString(root, "answerHintVi"), requireString(root, "answerHintEn"));
        } catch (GenerationRequestInvalidException e) {
            throw e;
        } catch (Exception e) {
            throw new GenerationRequestInvalidException(
                    QuestionGenerationConstants.GENERATION_SCHEMA_INVALID,
                    "Failed to parse JSON response");
        }
    }

    public ItemDraft parse(String json, String taskTypeKey) {
        try {
            JsonNode root = mapper.readTree(json);
            String type = taskTypeKey.toUpperCase();

            return switch (type) {
                case "MC_READING_SINGLE", "MC_READING_MULTIPLE" ->
                        parseChoiceDraft(root);
                case "FILL_IN_THE_BLANKS_DROPDOWN", "FILL_IN_THE_BLANKS_DRAG_AND_DROP" ->
                        parseBlankFillDraft(root);
                case "RE_ORDER_PARAGRAPHS" ->
                        parseReorderDraft(root);
                case "SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY", "READ_ALOUD" ->
                        parsePassageDraft(root);
                case "WRITE_FROM_DICTATION", "REPEAT_SENTENCE", "ANSWER_SHORT_QUESTION",
                     "SUMMARIZE_SPOKEN_TEXT", "MC_LISTENING_SINGLE", "MC_LISTENING_MULTIPLE",
                     "HIGHLIGHT_CORRECT_SUMMARY", "SELECT_MISSING_WORD", "FILL_IN_THE_BLANKS_TYPE_IN" ->
                        parseListeningDraft(root);
                case "HIGHLIGHT_INCORRECT_WORDS" ->
                        parseHiwDraft(root);
                case "RE_TELL_LECTURE" ->
                        parseListeningDraft(root);
                case "SUMMARIZE_GROUP_DISCUSSION", "RESPOND_TO_A_SITUATION" ->
                        parseDialogueDraft(root);
                case "DESCRIBE_IMAGE" ->
                        parseChartDraft(root);
                case QuestionGenerationConstants.PROMPT_KEY_DESCRIBE_IMAGE_PICTURE -> PictureDrafts.parse(root);
                default -> throw new GenerationRequestInvalidException(
                        QuestionGenerationConstants.GENERATION_SCHEMA_INVALID,
                        "Unsupported task type: " + taskTypeKey);
            };
        } catch (GenerationRequestInvalidException e) {
            throw e;
        } catch (Exception e) {
            throw new GenerationRequestInvalidException(
                    QuestionGenerationConstants.GENERATION_SCHEMA_INVALID,
                    "Failed to parse JSON response");
        }
    }

    private ItemDraft.ChoiceDraft parseChoiceDraft(JsonNode root) {
        String topic = requireString(root, "topic");
        String passage = requireString(root, "passage");
        String question = requireString(root, "question");
        List<String> options = requireStringList(root, "options");
        List<Integer> correctIndexes = requireIntList(root, "correctIndexes");

        return new ItemDraft.ChoiceDraft(topic, passage, question, options, correctIndexes);
    }

    private ItemDraft.BlankFillDraft parseBlankFillDraft(JsonNode root) {
        String topic = requireString(root, "topic");
        String passageWithSlots = requireString(root, "passageWithSlots");
        List<ItemDraft.BlankFillDraft.Slot> slots = new ArrayList<>();

        JsonNode slotsNode = root.get("slots");
        if (slotsNode == null || !slotsNode.isArray()) {
            throw new GenerationRequestInvalidException(
                    QuestionGenerationConstants.GENERATION_SCHEMA_INVALID,
                    "Missing or invalid slots array");
        }

        for (JsonNode slot : slotsNode) {
            String answer = requireString(slot, "answer");
            List<String> distractors = requireStringList(slot, "distractors");
            slots.add(new ItemDraft.BlankFillDraft.Slot(answer, distractors));
        }

        return new ItemDraft.BlankFillDraft(topic, GapMarkers.normalize(passageWithSlots), slots);
    }

    private ItemDraft.ReorderDraft parseReorderDraft(JsonNode root) {
        String topic = requireString(root, "topic");
        List<String> paragraphs = requireStringList(root, "paragraphs");
        return new ItemDraft.ReorderDraft(topic, paragraphs);
    }

    private ItemDraft.PassageDraft parsePassageDraft(JsonNode root) {
        String topic = requireString(root, "topic");
        String passage = optionalString(root, "passage");
        List<String> keyPoints = optionalStringList(root, "keyPoints");
        String essayPrompt = optionalString(root, "essayPrompt");

        return new ItemDraft.PassageDraft(topic, passage, keyPoints, essayPrompt);
    }

    private ItemDraft.ListeningDraft parseListeningDraft(JsonNode root) {
        String topic = requireString(root, "topic");
        String transcript = requireString(root, "transcript");
        String question = optionalString(root, "question");
        List<String> options = optionalStringList(root, "options");
        List<Integer> correctIndexes = optionalIntList(root, "correctIndexes");
        List<ItemDraft.ListeningDraft.Slot> blanks = new ArrayList<>();

        JsonNode blanksNode = root.get("blanks");
        if (blanksNode != null && blanksNode.isArray()) {
            for (JsonNode blank : blanksNode) {
                String answer = requireString(blank, "answer");
                List<String> distractors = optionalStringList(blank, "distractors");
                blanks.add(new ItemDraft.ListeningDraft.Slot(answer, distractors));
            }
        }

        return new ItemDraft.ListeningDraft(topic, transcript, question, options, correctIndexes,
                blanks.isEmpty() ? null : blanks, optionalString(root, "answer"));
    }

    private ItemDraft.DialogueDraft parseDialogueDraft(JsonNode root) {
        String topic = requireString(root, "topic");
        String situation = optionalString(root, "situation");
        List<ItemDraft.DialogueDraft.Turn> turns = new ArrayList<>();
        JsonNode turnsNode = root.get("turns");
        if (turnsNode == null || !turnsNode.isArray() || turnsNode.isEmpty()) {
            throw new GenerationRequestInvalidException(QuestionGenerationConstants.GENERATION_SCHEMA_INVALID,
                    "Missing or empty required field: turns");
        }
        for (JsonNode turn : turnsNode) {
            turns.add(new ItemDraft.DialogueDraft.Turn(requireString(turn, "speaker"), requireString(turn, "text")));
        }
        return new ItemDraft.DialogueDraft(topic, situation, turns, optionalStringList(root, "keyPoints"));
    }

    private ItemDraft.ChartDraft parseChartDraft(JsonNode root) {
        List<ItemDraft.ChartDraft.Series> series = new ArrayList<>();
        JsonNode seriesNode = root.get("series");
        if (seriesNode == null || !seriesNode.isArray() || seriesNode.isEmpty()) {
            throw new GenerationRequestInvalidException(QuestionGenerationConstants.GENERATION_SCHEMA_INVALID,
                    "Missing or empty required field: series");
        }
        for (JsonNode one : seriesNode) {
            List<Double> values = new ArrayList<>();
            JsonNode valuesNode = one.get("values");
            if (valuesNode == null || !valuesNode.isArray()) {
                throw new GenerationRequestInvalidException(QuestionGenerationConstants.GENERATION_SCHEMA_INVALID,
                        "Series needs a values array");
            }
            for (JsonNode value : valuesNode) {
                if (!value.isNumber()) {
                    throw new GenerationRequestInvalidException(QuestionGenerationConstants.GENERATION_SCHEMA_INVALID,
                            "Series values must be numbers");
                }
                values.add(value.asDouble());
            }
            series.add(new ItemDraft.ChartDraft.Series(requireString(one, "name"), values));
        }
        List<ItemDraft.ChartDraft.KeyPoint> keyPoints = new ArrayList<>();
        JsonNode keyNode = root.get("keyPoints");
        if (keyNode != null && keyNode.isArray()) {
            for (JsonNode point : keyNode) {
                String ref = optionalString(point, "reference");
                keyPoints.add(new ItemDraft.ChartDraft.KeyPoint(requireString(point, "kind").toUpperCase(),
                        ref != null ? ref : optionalString(point, "ref"), requireString(point, "text")));
            }
        }
        return new ItemDraft.ChartDraft(requireString(root, "topic"), requireString(root, "chartType").toUpperCase(),
                requireString(root, "title"), optionalString(root, "xLabel"), optionalString(root, "yLabel"),
                optionalString(root, "unit"), requireStringList(root, "categories"), series, keyPoints);
    }

    private ItemDraft.HiwDraft parseHiwDraft(JsonNode root) {
        String topic = requireString(root, "topic");
        String transcript = requireString(root, "transcript");
        String displayedText = requireString(root, "displayedText");
        List<Integer> changedIndexesList = requireIntList(root, "changedIndexes");
        int[] changedIndexes = changedIndexesList.stream().mapToInt(Integer::intValue).toArray();

        return new ItemDraft.HiwDraft(topic, transcript, displayedText, changedIndexes);
    }

    private String requireString(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new GenerationRequestInvalidException(
                    QuestionGenerationConstants.GENERATION_SCHEMA_INVALID,
                    "Missing or empty required field: " + field);
        }
        return value.asText();
    }

    private String optionalString(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private List<String> requireStringList(JsonNode node, String field) {
        JsonNode array = node.get(field);
        if (array == null || !array.isArray() || array.size() == 0) {
            throw new GenerationRequestInvalidException(
                    QuestionGenerationConstants.GENERATION_SCHEMA_INVALID,
                    "Missing or empty required array: " + field);
        }

        List<String> result = new ArrayList<>();
        for (JsonNode item : array) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new GenerationRequestInvalidException(
                        QuestionGenerationConstants.GENERATION_SCHEMA_INVALID,
                        "Empty item in array: " + field);
            }
            result.add(item.asText());
        }
        return result;
    }

    private List<String> optionalStringList(JsonNode node, String field) {
        JsonNode array = node.get(field);
        if (array == null || !array.isArray()) {
            return null;
        }

        List<String> result = new ArrayList<>();
        for (JsonNode item : array) {
            if (item.isTextual() && !item.asText().isBlank()) {
                result.add(item.asText());
            }
        }
        return result.isEmpty() ? null : result;
    }

    private List<Integer> requireIntList(JsonNode node, String field) {
        JsonNode array = node.get(field);
        if (array == null || !array.isArray() || array.size() == 0) {
            throw new GenerationRequestInvalidException(
                    QuestionGenerationConstants.GENERATION_SCHEMA_INVALID,
                    "Missing or empty required array: " + field);
        }

        List<Integer> result = new ArrayList<>();
        for (JsonNode item : array) {
            if (!item.isInt()) {
                throw new GenerationRequestInvalidException(
                        QuestionGenerationConstants.GENERATION_SCHEMA_INVALID,
                        "Non-integer item in array: " + field);
            }
            result.add(item.asInt());
        }
        return result;
    }

    private List<Integer> optionalIntList(JsonNode node, String field) {
        JsonNode array = node.get(field);
        if (array == null || !array.isArray()) {
            return null;
        }

        List<Integer> result = new ArrayList<>();
        for (JsonNode item : array) {
            if (item.isInt()) {
                result.add(item.asInt());
            }
        }
        return result.isEmpty() ? null : result;
    }
}
