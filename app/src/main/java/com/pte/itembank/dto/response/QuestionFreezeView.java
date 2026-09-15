package com.pte.itembank.dto.response;

import com.pte.itembank.domain.enums.PteTaskType;

import java.util.List;
import java.util.UUID;

/**
 * Full-fidelity, deep-copy-ready view of one question's content — the only
 * shape {@code assessment} is allowed to read when freezing a blueprint item
 * into a {@code SnapshotItem} (Design Constraints: assessment never touches
 * {@code QuestionRepository} directly). Options are already in delivery order
 * (see {@code ItembankService#freeze} for the {@code RE_ORDER_PARAGRAPHS}
 * rotation rule) — the caller must not re-sort them.
 */
public record QuestionFreezeView(
        UUID sourceQuestionPublicId,
        PteTaskType pteTaskType,
        String title,
        String promptText,
        UUID audioPromptRef,
        UUID imagePromptRef,
        String referenceAnswerText,
        String correctAnswerText,
        Integer minWordCount,
        Integer maxWordCount,
        List<Option> options) {

    public record Option(String text, boolean correct, int orderIndex, Integer blankIndex, Integer correctGapIndex) {
    }
}
