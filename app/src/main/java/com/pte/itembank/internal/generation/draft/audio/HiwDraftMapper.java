package com.pte.itembank.internal.generation.draft.audio;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import org.springframework.stereotype.Component;

/**
 * Maps HIGHLIGHT_INCORRECT_WORDS (HIW) draft to CreateQuestionRequest.
 * Handles the special formatting for changed word indices and transcript mapping.
 */
@Component
public class HiwDraftMapper {

    public CreateQuestionRequest mapHighlightIncorrectWords(
            ItemDraft.HiwDraft draft, CefrLevel cefr, QuestionPool pool) {
        String correctAnswerText = formatHiwCorrectAnswer(draft.changedIndexes());

        return new CreateQuestionRequest(
                "HIGHLIGHT_INCORRECT_WORDS",
                "HIGHLIGHT_INCORRECT_WORDS",
                draft.displayedText(),
                null, null,
                draft.transcript(),
                correctAnswerText,
                null, null,
                null,
                pool.name(),
                null, null,
                cefr.name());
    }

    /**
     * Format HIW correct answer as per ReferenceAnswerScorer.parseHighlightIncorrectReference.
     * Returns a JSON list of the zero-based positions of the changed words, as stored for the existing questions.
     */
    private String formatHiwCorrectAnswer(int[] changedIndexes) {
        if (changedIndexes == null || changedIndexes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < changedIndexes.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(changedIndexes[i]);
        }
        return sb.append("]").toString();
    }
}
