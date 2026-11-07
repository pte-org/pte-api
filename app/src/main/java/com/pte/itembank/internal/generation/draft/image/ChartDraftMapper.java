package com.pte.itembank.internal.generation.draft.image;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import org.springframework.stereotype.Component;

/**
 * Maps DESCRIBE_IMAGE (chart-based) draft to CreateQuestionRequest.
 * Key points become referenceAnswerText for the AI scorer to evaluate description quality.
 */
@Component
public class ChartDraftMapper {

    public CreateQuestionRequest mapDescribeImage(
            ItemDraft.ChartDraft draft, CefrLevel cefr, QuestionPool pool) {
        // Chart description: keyPoints become the reference answer (chart insights to describe)
        String referenceAnswer = draft.keyPoints() != null
                ? draft.keyPoints().stream()
                    .map(kp -> kp.text())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("")
                : "";

        return new CreateQuestionRequest(
                "DESCRIBE_IMAGE",
                "DESCRIBE_IMAGE",
                null, // promptText empty for audio
                null, null,
                referenceAnswer, // referenceAnswerText = chart insights
                null,
                null, null,
                null,
                pool.name(),
                null, null,
                cefr.name());
    }
}
