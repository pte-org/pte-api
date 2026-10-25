package com.pte.scoring.internal.service;

import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.dto.response.AnswerOptionView;
import com.pte.scoring.internal.dto.response.AnswerPayloadKind;
import com.pte.scoring.internal.dto.response.DecodedAnswerPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns {@code SubmittedAnswerView.payload} (opaque, per-task-type-encoded) into a
 * human-readable {@link DecodedAnswerPayload} for host review.
 *
 * <p>Deliberately NOT a 23-way switch over every PTE task type: decoding is
 * driven by two signals already present on the submitted answer — whether
 * the task type is one of the 8 Speaking types (answer is always a
 * recorded-audio media publicId), and whether {@code optionsJson} is
 * populated (answer is one or more option picks). The two structured
 * Listening task types are decoded by their named sets; the five remaining
 * types continue through the generic TEXT/SELECTION fallback.
 * FILL_IN_THE_BLANKS_TYPE_IN and HIGHLIGHT_INCORRECT_WORDS use explicit task-type
 * sets because their comma-separated payload shapes are ambiguous without
 * task context; malformed or unparseable payloads use {@link
 * AnswerPayloadKind#UNRECOGNIZED} rather than crashing.
 *
 * <p>Mirrors {@code ObjectiveScoringService}'s own option-parsing shape
 * (duplicated, not extracted — different needs: preserving positional
 * duplicates for display rather than deduping into a scoring set).
 */
@Service
public class AnswerPayloadDecoder {

    private static final Logger log = LoggerFactory.getLogger(AnswerPayloadDecoder.class);

    private static final Set<String> AUDIO_ANSWER_TASK_TYPES = Set.of(
            ScoringConstants.TASK_TYPE_PERSONAL_INTRODUCTION,
            ScoringConstants.TASK_TYPE_READ_ALOUD,
            ScoringConstants.TASK_TYPE_REPEAT_SENTENCE,
            ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE,
            ScoringConstants.TASK_TYPE_RE_TELL_LECTURE,
            ScoringConstants.TASK_TYPE_ANSWER_SHORT_QUESTION,
            ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION,
            ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION);

    private static final Set<String> FILL_IN_THE_BLANKS_TYPE_IN_TASK_TYPES = Set.of(
            ScoringConstants.TASK_TYPE_FILL_IN_THE_BLANKS_TYPE_IN);

    private static final Set<String> HIGHLIGHT_INCORRECT_WORDS_TASK_TYPES = Set.of(
            ScoringConstants.TASK_TYPE_HIGHLIGHT_INCORRECT_WORDS);

    private static final String EMPTY_OPTIONS = "[]";

    private final JsonMapper jsonMapper;

    public AnswerPayloadDecoder(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public DecodedAnswerPayload decode(SubmittedAnswerView answer) {
        String route = runtimeRoute(answer);
        if (route != null && (AUDIO_ANSWER_TASK_TYPES.contains(route) || route.endsWith("_V1")
                && Set.of("PERSONAL_INTRODUCTION_V1", "READ_ALOUD_V1", "REPEAT_SENTENCE_V1",
                        "DESCRIBE_IMAGE_V1", "RE_TELL_LECTURE_V1", "ANSWER_SHORT_QUESTION_V1",
                        "RESPOND_TO_A_SITUATION_V1", "SUMMARIZE_GROUP_DISCUSSION_V1").contains(route))) {
            return decodeAudio(answer);
        }
        if (route != null
                && (FILL_IN_THE_BLANKS_TYPE_IN_TASK_TYPES.contains(route)
                        || "FILL_IN_THE_BLANKS_TYPE_IN_V1".equals(route))
                && !hasOptions(answer)) {
            return decodePositionalSelection(answer);
        }
        if (hasOptions(answer)) {
            return decodeSelection(answer);
        }
        if (route != null
                && (HIGHLIGHT_INCORRECT_WORDS_TASK_TYPES.contains(route)
                        || "HIGHLIGHT_INCORRECT_WORDS_V1".equals(route))
                && !hasOptions(answer)) {
            return decodeWordIndices(answer);
        }
        return new DecodedAnswerPayload(AnswerPayloadKind.TEXT, answer.payload(), null, null, null, null, null);
    }

    /**
     * Snapshots store {@code "[]"} for questions without options (essay,
     * speaking, ...), so an empty array means "no options", not "unparsable".
     */
    private boolean hasOptions(SubmittedAnswerView answer) {
        String optionsJson = answer.optionsJson();
        return optionsJson != null && !optionsJson.isBlank() && !EMPTY_OPTIONS.equals(optionsJson.replaceAll("\\s", ""));
    }

    private String runtimeRoute(SubmittedAnswerView answer) {
        return answer.runtime() != null ? answer.runtime().rendererKey() : answer.taskType();
    }

    private DecodedAnswerPayload decodeAudio(SubmittedAnswerView answer) {
        String payload = answer.payload();
        if (payload == null || payload.isBlank()) {
            return unrecognized(answer, "empty audio answer payload");
        }
        try {
            UUID mediaPublicId = UUID.fromString(payload.trim());
            return new DecodedAnswerPayload(AnswerPayloadKind.AUDIO, null, mediaPublicId, null, null, null, null);
        } catch (IllegalArgumentException ex) {
            return unrecognized(answer, "audio answer payload is not a UUID");
        }
    }

    private DecodedAnswerPayload decodeSelection(SubmittedAnswerView answer) {
        List<FrozenOption> allOptions = parseOptions(answer.optionsJson());
        if (allOptions.isEmpty()) {
            return unrecognized(answer, "optionsJson present but unparsable/empty");
        }
        Set<Integer> selectedOrderIndexes = parsePositionalOrderIndexes(answer.payload()).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<AnswerOptionView> options = allOptions.stream()
                .map(option -> new AnswerOptionView(option.orderIndex(), option.text(), option.correct(),
                        selectedOrderIndexes.contains(option.orderIndex())))
                .toList();
        return new DecodedAnswerPayload(AnswerPayloadKind.SELECTION, null, null, options, null, null, null);
    }

    private DecodedAnswerPayload decodePositionalSelection(SubmittedAnswerView answer) {
        String payload = answer.payload();
        if (payload == null) {
            return unrecognized(answer, "positional selection payload is null");
        }
        List<String> gapValues = new ArrayList<>();
        for (String part : payload.split(",", -1)) {
            gapValues.add(part.isEmpty() ? null : part);
        }
        return new DecodedAnswerPayload(
                AnswerPayloadKind.POSITIONAL_SELECTION, null, null, null, null, gapValues, null);
    }

    private DecodedAnswerPayload decodeWordIndices(SubmittedAnswerView answer) {
        String payload = answer.payload();
        if (payload == null) {
            return unrecognized(answer, "word-index payload is null");
        }
        if (payload.isEmpty()) {
            return new DecodedAnswerPayload(AnswerPayloadKind.WORD_INDICES, null, null, null, null,
                    null, List.of());
        }

        List<Integer> wordIndices = new ArrayList<>();
        for (String part : payload.split(",", -1)) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                return unrecognized(answer, "word-index payload contains an empty entry");
            }
            try {
                int wordIndex = Integer.parseInt(trimmed);
                if (wordIndex < 0) {
                    return unrecognized(answer, "word-index payload contains a negative index");
                }
                wordIndices.add(wordIndex);
            } catch (NumberFormatException ex) {
                return unrecognized(answer, "word-index payload contains a non-integer entry");
            }
        }
        return new DecodedAnswerPayload(AnswerPayloadKind.WORD_INDICES, null, null, null, null,
                null, wordIndices);
    }

    private DecodedAnswerPayload unrecognized(SubmittedAnswerView answer, String reason) {
        log.warn("Could not decode answer payload for review (answerPublicId={}, taskType={}): {}",
                answer.answerPublicId(), answer.taskType(), reason);
        return new DecodedAnswerPayload(AnswerPayloadKind.UNRECOGNIZED, answer.payload(), null, null, null, null, null);
    }

    private List<FrozenOption> parseOptions(String optionsJson) {
        try {
            return jsonMapper.readValue(optionsJson, new TypeReference<List<FrozenOption>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    /**
     * Positional orderIndex list — mirrors {@code ObjectiveScoringService}'s
     * own parser exactly (same {@code split(",", -1)} trailing-empty-entry
     * requirement), but returns every position (including duplicates) for
     * display rather than deduping into a scoring set.
     */
    private List<Integer> parsePositionalOrderIndexes(String payload) {
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
     * Field-for-field identical to {@code ObjectiveScoringService.FrozenOption}
     * (same authoring-produced JSON shape) — {@code blankIndex}/{@code
     * correctGapIndex} are unused here but MUST stay declared, otherwise Jackson's
     * default fail-on-unknown-properties would throw on every fill-blanks
     * answer's optionsJson and silently degrade it to UNRECOGNIZED.
     */
    private record FrozenOption(String text, boolean correct, int orderIndex, Integer blankIndex, Integer correctGapIndex) {
    }
}
