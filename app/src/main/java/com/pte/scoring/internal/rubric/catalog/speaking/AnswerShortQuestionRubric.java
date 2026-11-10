package com.pte.scoring.internal.rubric.catalog.speaking;

import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.catalog.CatalogSupport;
import com.pte.scoring.rubric.RubricTraits;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitScale;
import com.pte.scoring.rubric.TraitSource;

import java.util.List;
import java.util.Set;

/** Answer Short Question (Score Guide p.24): one point for an appropriate word; scored for Listening only. */
public final class AnswerShortQuestionRubric {

    private AnswerShortQuestionRubric() {
    }

    public static TaskRubric create() {
        return new TaskRubric(ScoringConstants.TASK_TYPE_ANSWER_SHORT_QUESTION,
                Set.of(RubricConstants.SKILL_LISTENING),
                List.of(CatalogSupport.trait(RubricTraits.VOCABULARY, "Từ vựng", "Vocabulary", TraitScale.fixed(1),
                        TraitSource.MECHANICAL, CatalogSupport.levels(
                                "Inappropriate word choice in response.",
                                "Appropriate word choice in response."))),
                Set.of(), null, null);
    }
}
