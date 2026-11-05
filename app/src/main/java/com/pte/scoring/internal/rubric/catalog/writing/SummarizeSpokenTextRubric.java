package com.pte.scoring.internal.rubric.catalog.writing;

import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.catalog.CatalogSupport;
import com.pte.scoring.rubric.FormBand;
import com.pte.scoring.rubric.FormRule;
import com.pte.scoring.rubric.RubricTraits;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitDefinition;
import com.pte.scoring.rubric.TraitScale;
import com.pte.scoring.rubric.TraitSource;

import java.util.List;
import java.util.Set;

/** Summarize Spoken Text (Score Guide pp.41-43): Form 2 for 50-70 words, 1 for 40-49 or 71-100 words. */
public final class SummarizeSpokenTextRubric {

    private static final String PAGES = "tr.41-43";

    private SummarizeSpokenTextRubric() {
    }

    public static TaskRubric create() {
        return new TaskRubric(ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT,
                Set.of(RubricConstants.SKILL_LISTENING, RubricConstants.SKILL_WRITING),
                List.of(SummaryTraits.content("tr.41-42"), form(), SummaryTraits.grammar("tr.42"),
                        SummaryTraits.vocabulary("tr.43"), SummaryTraits.spelling("tr.43")),
                Set.of(RubricTraits.CONTENT, RubricTraits.FORM),
                new FormRule(List.of(new FormBand(50, 70, 2), new FormBand(40, 49, 1), new FormBand(71, 100, 1)),
                        false, true), null, PAGES);
    }

    private static TraitDefinition form() {
        return CatalogSupport.trait(RubricTraits.FORM, "Hình thức", "Form", TraitScale.fixed(2),
                TraitSource.MECHANICAL, "tr.42", CatalogSupport.levels(
                        "Contains less than 40 words or more than 100 words. Summary is written in capital "
                                + "letters, contains no punctuation or consists only of bullet points or very short "
                                + "sentences.",
                        "Contains 40-49 words or 71-100 words.",
                        "Contains 50-70 words."));
    }
}
