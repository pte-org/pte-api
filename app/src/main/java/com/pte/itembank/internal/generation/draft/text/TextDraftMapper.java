package com.pte.itembank.internal.generation.draft.text;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.dto.request.OptionRequest;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps text-based ItemDraft types to CreateQuestionRequest.
 * Handles: MC_READING_SINGLE/MULTIPLE, FIB (dropdown/drag), REORDER, SWT, WRITE_ESSAY, READ_ALOUD.
 */
@Component
public class TextDraftMapper {

    private static final int DEFAULT_MIN_WORD_COUNT_WE = 200;
    private static final int DEFAULT_MAX_WORD_COUNT_WE = 300;
    private static final int DEFAULT_MIN_WORD_COUNT_SWT = 30;
    private static final int DEFAULT_MAX_WORD_COUNT_SWT = 75;

    public CreateQuestionRequest mapChoiceSingle(ItemDraft.ChoiceDraft draft, CefrLevel cefr, QuestionPool pool) {
        String promptText = draft.passage() + "\n\n" + draft.question();
        List<OptionRequest> options = new ArrayList<>();

        for (int i = 0; i < draft.options().size(); i++) {
            boolean isCorrect = draft.correctIndexes().contains(i);
            options.add(new OptionRequest(draft.options().get(i), isCorrect, i));
        }

        return new CreateQuestionRequest(
                "MC_READING_SINGLE",
                "MC_READING_SINGLE",
                promptText,
                null, null,
                null, null,
                null, null,
                options,
                pool.name(),
                null, null,
                cefr.name());
    }

    public CreateQuestionRequest mapChoiceMultiple(ItemDraft.ChoiceDraft draft, CefrLevel cefr, QuestionPool pool) {
        String promptText = draft.passage() + "\n\n" + draft.question();
        List<OptionRequest> options = new ArrayList<>();

        for (int i = 0; i < draft.options().size(); i++) {
            boolean isCorrect = draft.correctIndexes().contains(i);
            options.add(new OptionRequest(draft.options().get(i), isCorrect, i));
        }

        return new CreateQuestionRequest(
                "MC_READING_MULTIPLE",
                "MC_READING_MULTIPLE",
                promptText,
                null, null,
                null, null,
                null, null,
                options,
                pool.name(),
                null, null,
                cefr.name());
    }

    public CreateQuestionRequest mapBlankFillDropdown(ItemDraft.BlankFillDraft draft, CefrLevel cefr, QuestionPool pool) {
        List<OptionRequest> options = new ArrayList<>();
        int optionIndex = 0;

        for (int blankIndex = 0; blankIndex < draft.slots().size(); blankIndex++) {
            var slot = draft.slots().get(blankIndex);

            options.add(new OptionRequest(slot.answer(), true, optionIndex, blankIndex, null));
            optionIndex++;

            for (String distractor : slot.distractors()) {
                options.add(new OptionRequest(distractor, false, optionIndex, blankIndex, null));
                optionIndex++;
            }
        }

        return new CreateQuestionRequest(
                "FILL_IN_THE_BLANKS_DROPDOWN",
                "FILL_IN_THE_BLANKS_DROPDOWN",
                draft.passageWithSlots(),
                null, null,
                null, null,
                null, null,
                options,
                pool.name(),
                null, null,
                cefr.name());
    }

    public CreateQuestionRequest mapBlankFillDragDrop(ItemDraft.BlankFillDraft draft, CefrLevel cefr, QuestionPool pool) {
        List<OptionRequest> options = new ArrayList<>();
        int orderIndex = 0;

        for (int blankIndex = 0; blankIndex < draft.slots().size(); blankIndex++) {
            var slot = draft.slots().get(blankIndex);

            // Drag-drop stores the slot a word belongs to in correctGapIndex only; the bank rejects both indexes together.
            options.add(new OptionRequest(slot.answer(), true, orderIndex, null, blankIndex));
            orderIndex++;

            for (String distractor : slot.distractors()) {
                options.add(new OptionRequest(distractor, false, orderIndex, null, null));
                orderIndex++;
            }
        }

        return new CreateQuestionRequest(
                "FILL_IN_THE_BLANKS_DRAG_AND_DROP",
                "FILL_IN_THE_BLANKS_DRAG_AND_DROP",
                draft.passageWithSlots(),
                null, null,
                null, null,
                null, null,
                options,
                pool.name(),
                null, null,
                cefr.name());
    }

    public CreateQuestionRequest mapReorder(ItemDraft.ReorderDraft draft, CefrLevel cefr, QuestionPool pool) {
        List<OptionRequest> options = new ArrayList<>();

        for (int i = 0; i < draft.paragraphs().size(); i++) {
            options.add(new OptionRequest(draft.paragraphs().get(i), true, i));
        }

        String promptText = "Put the following paragraphs in the correct order.";

        return new CreateQuestionRequest(
                "RE_ORDER_PARAGRAPHS",
                "RE_ORDER_PARAGRAPHS",
                promptText,
                null, null,
                null, null,
                null, null,
                options,
                pool.name(),
                null, null,
                cefr.name());
    }

    public CreateQuestionRequest mapSummary(ItemDraft.PassageDraft draft, CefrLevel cefr, QuestionPool pool) {
        String referenceAnswer = draft.keyPoints() != null
                ? String.join("\n", draft.keyPoints())
                : "";

        return new CreateQuestionRequest(
                "SUMMARIZE_WRITTEN_TEXT",
                "SUMMARIZE_WRITTEN_TEXT",
                draft.passage(),
                null, null,
                referenceAnswer, null,
                DEFAULT_MIN_WORD_COUNT_SWT, DEFAULT_MAX_WORD_COUNT_SWT,
                null,
                pool.name(),
                null, null,
                cefr.name());
    }

    public CreateQuestionRequest mapEssay(ItemDraft.PassageDraft draft, CefrLevel cefr, QuestionPool pool) {
        return new CreateQuestionRequest(
                "WRITE_ESSAY",
                "WRITE_ESSAY",
                draft.essayPrompt(),
                null, null,
                null, null,
                DEFAULT_MIN_WORD_COUNT_WE, DEFAULT_MAX_WORD_COUNT_WE,
                null,
                pool.name(),
                null, null,
                cefr.name());
    }

    public CreateQuestionRequest mapReadAloud(ItemDraft.PassageDraft draft, CefrLevel cefr, QuestionPool pool) {
        return new CreateQuestionRequest(
                "READ_ALOUD",
                "READ_ALOUD",
                draft.passage(),
                null, null,
                null, null,
                null, null,
                null,
                pool.name(),
                null, null,
                cefr.name());
    }
}
