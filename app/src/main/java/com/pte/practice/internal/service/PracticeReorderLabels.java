package com.pte.practice.internal.service;

import com.pte.itembank.dto.response.PracticeQuestionContent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Re-order Paragraphs stores each paragraph's correct position as its {@code orderIndex}, so sending that value to
 * the student would hand over the answer. The pinned student view instead lists the paragraphs shuffled and labels
 * them by their shown position; the answer key keeps the original indexes and the scorer maps labels back by
 * paragraph text (see {@link PracticeAnswerCanonicalizer}). Items pinned before this change keep original labels,
 * which map back to themselves.
 */
public final class PracticeReorderLabels {

    public static final String TASK_CODE = "RE_ORDER_PARAGRAPHS";

    private PracticeReorderLabels() {
    }

    /** Shuffles the paragraphs so they are never in answer order, then labels them by shown position. */
    public static List<PracticeQuestionContent.Option> shuffleAndRelabel(
            List<PracticeQuestionContent.Option> options, Random random) {
        if (options == null || options.size() < 2) {
            return options;
        }
        List<PracticeQuestionContent.Option> shuffled = new ArrayList<>(options);
        do {
            Collections.shuffle(shuffled, random);
        } while (inAnswerOrder(shuffled));
        List<PracticeQuestionContent.Option> labelled = new ArrayList<>(shuffled.size());
        for (int position = 0; position < shuffled.size(); position++) {
            PracticeQuestionContent.Option option = shuffled.get(position);
            labelled.add(new PracticeQuestionContent.Option(option.publicId(), option.text(), position,
                    option.blankIndex()));
        }
        return labelled;
    }

    private static boolean inAnswerOrder(List<PracticeQuestionContent.Option> options) {
        for (int index = 1; index < options.size(); index++) {
            if (options.get(index - 1).orderIndex() > options.get(index).orderIndex()) {
                return false;
            }
        }
        return true;
    }
}
