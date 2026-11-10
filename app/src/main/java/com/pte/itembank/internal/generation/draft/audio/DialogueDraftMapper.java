package com.pte.itembank.internal.generation.draft.audio;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Maps dialogue-based ItemDraft types to CreateQuestionRequest.
 * Handles: SUMMARIZE_GROUP_DISCUSSION, RESPOND_TO_A_SITUATION.
 * Key points become referenceAnswerText per phase 6 decision.
 */
@Component
public class DialogueDraftMapper {

    private static final int DEFAULT_MIN_WORD_COUNT_SWT = 30;
    private static final int DEFAULT_MAX_WORD_COUNT_SWT = 75;

    public CreateQuestionRequest mapSummarizeGroupDiscussion(
            ItemDraft.DialogueDraft draft, CefrLevel cefr, QuestionPool pool) {
        // SGD: keyPoints become referenceAnswerText (for AI scorer trait Content)
        String referenceAnswer = draft.keyPoints() != null
                ? String.join("\n", draft.keyPoints())
                : "";

        return new CreateQuestionRequest(
                "SUMMARIZE_GROUP_DISCUSSION",
                "SUMMARIZE_GROUP_DISCUSSION",
                null, // promptText empty for audio
                null, null,
                referenceAnswer, // keyPoints as reference
                null,
                DEFAULT_MIN_WORD_COUNT_SWT, DEFAULT_MAX_WORD_COUNT_SWT,
                null,
                pool.name(),
                null, null,
                cefr.name());
    }

    public CreateQuestionRequest mapRespondToASituation(
            ItemDraft.DialogueDraft draft, CefrLevel cefr, QuestionPool pool) {
        // RTS: situation in promptText, keyPoints become referenceAnswerText
        String referenceAnswer = draft.keyPoints() != null
                ? String.join("\n", draft.keyPoints())
                : "";

        return new CreateQuestionRequest(
                "RESPOND_TO_A_SITUATION",
                "RESPOND_TO_A_SITUATION",
                draft.situation(), // promptText = situation for student
                null, null,
                referenceAnswer, // keyPoints as reference for scorer
                null,
                DEFAULT_MIN_WORD_COUNT_SWT, DEFAULT_MAX_WORD_COUNT_SWT,
                null,
                pool.name(),
                null, null,
                cefr.name());
    }
}
