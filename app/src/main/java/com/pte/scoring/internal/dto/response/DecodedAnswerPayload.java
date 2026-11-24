package com.pte.scoring.internal.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * A human-readable view of {@code SubmittedAnswerView.payload}. Fields are
 * mutually exclusive per {@code kind}: {@code AUDIO} sets {@code
 * mediaPublicId} (always) and {@code mediaUrl} (only if presigning it
 * succeeded); {@code TEXT}/{@code UNRECOGNIZED} set only {@code text}; {@code
 * SELECTION} sets only {@code options}; {@code POSITIONAL_SELECTION} sets
 * only {@code gapValues} (with {@code null} entries for unanswered gaps), plus
 * {@code correctGapValues} when the item's answer key is a readable per-gap list;
 * {@code WORD_INDICES} sets only {@code wordIndices}. A re-order {@code SELECTION}
 * also sets {@code submittedOrder}: the {@code orderIndex} of each paragraph in the
 * order the student arranged them.
 */
public record DecodedAnswerPayload(AnswerPayloadKind kind, String text, UUID mediaPublicId,
                                    List<AnswerOptionView> options, String mediaUrl,
                                    List<String> gapValues, List<Integer> wordIndices,
                                    List<String> correctGapValues, List<Integer> submittedOrder) {

    public DecodedAnswerPayload(AnswerPayloadKind kind, String text, UUID mediaPublicId,
                                 List<AnswerOptionView> options, String mediaUrl,
                                 List<String> gapValues, List<Integer> wordIndices) {
        this(kind, text, mediaPublicId, options, mediaUrl, gapValues, wordIndices, null, null);
    }
}
