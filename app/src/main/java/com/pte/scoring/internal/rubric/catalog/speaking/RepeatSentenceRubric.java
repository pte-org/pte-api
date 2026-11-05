package com.pte.scoring.internal.rubric.catalog.speaking;

import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.catalog.CatalogSupport;
import com.pte.scoring.internal.rubric.catalog.PronunciationFluencyTraits;
import com.pte.scoring.rubric.RubricTraits;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitDefinition;
import com.pte.scoring.rubric.TraitScale;
import com.pte.scoring.rubric.TraitSource;

import java.util.List;
import java.util.Set;

/** Repeat Sentence (Score Guide pp.16-17): Content is banded by the share of words repeated in order. */
public final class RepeatSentenceRubric {

    private static final String PAGES = "tr.16-17";
    private static final int CONTENT_MAX = 3;

    private RepeatSentenceRubric() {
    }

    public static TaskRubric create() {
        return new TaskRubric(ScoringConstants.TASK_TYPE_REPEAT_SENTENCE,
                Set.of(RubricConstants.SKILL_LISTENING, RubricConstants.SKILL_SPEAKING),
                List.of(content(), PronunciationFluencyTraits.pronunciation(PAGES),
                        PronunciationFluencyTraits.oralFluency(PAGES)),
                Set.of(), null, null, PAGES);
    }

    private static TraitDefinition content() {
        return CatalogSupport.trait(RubricTraits.CONTENT, "Nội dung (từ lặp lại đúng thứ tự)", "Content",
                TraitScale.fixed(CONTENT_MAX), TraitSource.MECHANICAL, PAGES, CatalogSupport.levels(
                        "Almost nothing from the prompt in the response.",
                        "Less than 50% of words in the response from the prompt in the correct sequence.",
                        "At least 50% of words in the response from the prompt in the correct sequence.",
                        "All words in the response from the prompt in the correct sequence."));
    }
}
