package com.pte.scoring.service;

import com.pte.scoring.constant.ScoringConstants;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.dto.response.AnswerOptionView;
import com.pte.scoring.dto.response.AnswerPayloadKind;
import com.pte.scoring.dto.response.DecodedAnswerPayload;
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
 * Turns {@code ScoringAnswer.payload} (opaque, per-task-type-encoded — see
 * exam-delivery's {@code SubmitAnswerRequest} doc comment for the source
 * contract) into a human-readable {@link DecodedAnswerPayload} for host
 * review.
 *
 * <p>Deliberately NOT a 23-way switch over every PTE task type (one per
 * {@code PteTaskType} enum constant): decoding is driven by two signals
 * already present on {@code ScoringAnswer} — whether the task type is one of
 * the 8 Speaking types (answer is always a recorded-audio media publicId,
 * regardless of that type's own question-authoring {@code requiresAudioPrompt}
 * flag, which is unrelated), and whether {@code optionsJson} is populated
 * (answer is one or more option picks). The two structured Listening task
 * types are decoded by their named sets; the five remaining types continue
 * through the generic
 * TEXT/SELECTION fallback. FILL_BLANKS_LISTENING and
 * HIGHLIGHT_INCORRECT_WORDS use explicit task-type sets because their
 * comma-separated payload shapes are ambiguous without task context;
 * malformed or unparseable payloads use
 * {@link AnswerPayloadKind#UNRECOGNIZED} rather than crashing.
 *
 * <p>Mirrors {@code ObjectiveScoringService}'s own option-parsing shape
 * (duplicated, not extracted — that class's {@code FrozenOption}/parsing
 * helpers are private and scoring-answer decoding has different needs, e.g.
 * preserving positional duplicates for display rather than deduping into a
 * scoring set; consistent with this codebase's preference for small
 * duplication over a premature shared abstraction).
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

    private static final Set<String> FILL_BLANKS_LISTENING_TASK_TYPES = Set.of(
            ScoringConstants.TASK_TYPE_FILL_BLANKS_LISTENING);

    private static final Set<String> HIGHLIGHT_INCORRECT_WORDS_TASK_TYPES = Set.of(
            ScoringConstants.TASK_TYPE_HIGHLIGHT_INCORRECT_WORDS);

    private final JsonMapper jsonMapper;

    public AnswerPayloadDecoder(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public DecodedAnswerPayload decode(ScoringAnswer answer) {
        if (answer.getTaskType() != null && AUDIO_ANSWER_TASK_TYPES.contains(answer.getTaskType())) {
            return decodeAudio(answer);
        }
        if (answer.getTaskType() != null
                && FILL_BLANKS_LISTENING_TASK_TYPES.contains(answer.getTaskType())
                && answer.getOptionsJson() == null) {
            return decodePositionalSelection(answer);
        }
        if (answer.getOptionsJson() != null && !answer.getOptionsJson().isBlank()) {
            return decodeSelection(answer);
        }
        if (answer.getTaskType() != null
                && HIGHLIGHT_INCORRECT_WORDS_TASK_TYPES.contains(answer.getTaskType())
                && answer.getOptionsJson() == null) {
            return decodeWordIndices(answer);
        }
        return new DecodedAnswerPayload(AnswerPayloadKind.TEXT, answer.getPayload(), null, null, null, null, null);
    }

    private DecodedAnswerPayload decodeAudio(ScoringAnswer answer) {
        String payload = answer.getPayload();
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

    private DecodedAnswerPayload decodeSelection(ScoringAnswer answer) {
        List<FrozenOption> allOptions = parseOptions(answer.getOptionsJson());
        if (allOptions.isEmpty()) {
            return unrecognized(answer, "optionsJson present but unparsable/empty");
        }
        Set<Integer> selectedOrderIndexes = parsePositionalOrderIndexes(answer.getPayload()).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<AnswerOptionView> options = allOptions.stream()
                .map(option -> new AnswerOptionView(option.orderIndex(), option.text(), option.correct(),
                        selectedOrderIndexes.contains(option.orderIndex())))
                .toList();
        return new DecodedAnswerPayload(AnswerPayloadKind.SELECTION, null, null, options, null, null, null);
    }

    private DecodedAnswerPayload decodePositionalSelection(ScoringAnswer answer) {
        String payload = answer.getPayload();
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

    private DecodedAnswerPayload decodeWordIndices(ScoringAnswer answer) {
        String payload = answer.getPayload();
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

    private DecodedAnswerPayload unrecognized(ScoringAnswer answer, String reason) {
        log.warn("Could not decode answer payload for review (answerPublicId={}, taskType={}): {}",
                answer.getAnswerPublicId(), answer.getTaskType(), reason);
        return new DecodedAnswerPayload(AnswerPayloadKind.UNRECOGNIZED, answer.getPayload(), null, null, null, null, null);
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
