package com.pte.scoring.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * A human-readable view of {@code ScoringAnswer.payload}. Fields are
 * mutually exclusive per {@code kind} (mirrors {@code TaskView}'s own
 * flat-nullable-fields convention in exam-delivery, not a polymorphic
 * subtype hierarchy — no such pattern exists anywhere else in this codebase):
 * {@code AUDIO} sets {@code mediaPublicId} (always) and {@code mediaUrl}
 * (only if presigning it succeeded — see {@code AnswerPayloadDecoder}'s doc,
 * presigning itself happens one layer up in the detail service, not in the
 * decoder); {@code TEXT}/{@code UNRECOGNIZED} set only {@code text};
 * {@code SELECTION} sets only {@code options}.
 */
public record DecodedAnswerPayload(AnswerPayloadKind kind, String text, UUID mediaPublicId,
                                    List<AnswerOptionView> options, String mediaUrl) {
}
