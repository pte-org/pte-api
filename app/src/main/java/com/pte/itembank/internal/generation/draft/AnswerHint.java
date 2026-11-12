package com.pte.itembank.internal.generation.draft;

import com.pte.itembank.dto.request.CreateQuestionRequest;

/** Student-facing guidance generated with an item: how to approach it, never the answer. */
public record AnswerHint(String vi, String en) {

    /** The hints already carried by a question request; empty fields when it has none. */
    public static AnswerHint of(CreateQuestionRequest request) {
        return new AnswerHint(request.answerHintVi(), request.answerHintEn());
    }
}
