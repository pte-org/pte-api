package com.pte.scoring.internal.rubric.catalog.speaking;

import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.catalog.CatalogSupport;
import com.pte.scoring.internal.rubric.catalog.PronunciationFluencyTraits;
import com.pte.scoring.rubric.RubricTraits;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitScale;
import com.pte.scoring.rubric.TraitSource;

import java.util.List;
import java.util.Set;

/** Read Aloud (Score Guide pp.15-16): Content counts word errors, so its maximum is the length of the passage. */
public final class ReadAloudRubric {

    private ReadAloudRubric() {
    }

    public static TaskRubric create() {
        return new TaskRubric(ScoringConstants.TASK_TYPE_READ_ALOUD, Set.of(RubricConstants.SKILL_SPEAKING),
                List.of(content(), PronunciationFluencyTraits.pronunciation(),
                        PronunciationFluencyTraits.oralFluency()),
                Set.of(), null, null);
    }

    private static com.pte.scoring.rubric.TraitDefinition content() {
        return CatalogSupport.trait(RubricTraits.CONTENT, "Nội dung (số từ đúng)", "Content", TraitScale.perWord(),
                TraitSource.MECHANICAL, CatalogSupport.levels(
                        "Each replacement, omission or insertion of a word counts as one error. Maximum score: "
                                + "depends on the length of the question prompt (one point per word of the passage, "
                                + "minus errors, never below 0). Hesitations, filled or unfilled pauses, leading or "
                                + "trailing material are ignored in the scoring of content."));
    }
}
