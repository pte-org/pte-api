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

/** Summarize Written Text (Score Guide pp.32-33): one single sentence of 5 to 75 words. */
public final class SummarizeWrittenTextRubric {

    private static final String PAGES = "tr.32-33";
    private static final int MIN_WORDS = 5;
    private static final int MAX_WORDS = 75;

    private SummarizeWrittenTextRubric() {
    }

    public static TaskRubric create() {
        return new TaskRubric(ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT,
                Set.of(RubricConstants.SKILL_READING, RubricConstants.SKILL_WRITING),
                List.of(SummaryTraits.content("tr.32-33"), form(), SummaryTraits.grammar("tr.33"),
                        SummaryTraits.vocabulary("tr.33")),
                Set.of(RubricTraits.CONTENT, RubricTraits.FORM),
                new FormRule(List.of(new FormBand(MIN_WORDS, MAX_WORDS, 1)), true, true), null, PAGES);
    }

    private static TraitDefinition form() {
        return CatalogSupport.trait(RubricTraits.FORM, "Hình thức", "Form", TraitScale.fixed(1),
                TraitSource.MECHANICAL, "tr.33", CatalogSupport.levels(
                        "Not written in one single, complete sentence or contains fewer than 5 or more than 75 "
                                + "words. Summary is written in capital letters.",
                        "Is written in one, single, complete sentence."));
    }
}
