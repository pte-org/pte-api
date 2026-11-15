package com.pte.itembank.internal.generation.validate;

import com.pte.itembank.internal.generation.draft.ItemDraft;
import java.util.ArrayList;
import java.util.List;

/**
 * Common validation rules for all drafts: no empty fields, English language check.
 */
public final class CommonDraftValidator {

    private static final double MIN_ASCII_RATIO = 0.95;

    private CommonDraftValidator() {
    }

    static List<ValidationIssue> validateCommon(ItemDraft draft) {
        var issues = new ArrayList<ValidationIssue>();

        // Check topic is not empty
        if (draft.topic() == null || draft.topic().isBlank()) {
            issues.add(new ValidationIssue("EMPTY_TOPIC", "Topic cannot be empty."));
        }

        // Check for English language (95% ASCII or higher)
        String mainText = extractMainText(draft);
        if (!isEnglish(mainText)) {
            issues.add(new ValidationIssue("NOT_ENGLISH", "Content must be primarily in English."));
        }

        return issues;
    }

    private static String extractMainText(ItemDraft draft) {
        return switch (draft) {
            case ItemDraft.ChoiceDraft d -> d.passage() + " " + d.question();
            case ItemDraft.BlankFillDraft d -> d.passageWithSlots();
            case ItemDraft.ReorderDraft d -> String.join(" ", d.paragraphs());
            case ItemDraft.PassageDraft d -> d.mainText();
            case ItemDraft.ListeningDraft d -> d.transcript() + " " + d.question();
            case ItemDraft.HiwDraft d -> d.transcript() + " " + d.displayedText();
            case ItemDraft.DialogueDraft d -> d.situation() + " " + d.turns().stream()
                    .map(ItemDraft.DialogueDraft.Turn::text).collect(java.util.stream.Collectors.joining(" "));
            case ItemDraft.ChartDraft d -> d.title() + " " + String.join(" ", d.categories());
            case ItemDraft.PictureDraft d -> d.title() + " " + d.imagePrompt() + " " + d.referenceDescription();
        };
    }

    private static boolean isEnglish(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        long asciiCount = text.chars()
                .filter(c -> c < 128)
                .count();

        double ratio = (double) asciiCount / text.length();
        return ratio >= MIN_ASCII_RATIO;
    }
}
