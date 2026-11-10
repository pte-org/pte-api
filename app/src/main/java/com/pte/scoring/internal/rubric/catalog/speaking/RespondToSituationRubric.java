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

/** Respond to a Situation (Score Guide pp.29-31): Content 0-6 gates Pronunciation and Oral Fluency. */
public final class RespondToSituationRubric {
    private static final int CONTENT_MAX = 6;

    private RespondToSituationRubric() {
    }

    public static TaskRubric create() {
        return new TaskRubric(ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION,
                Set.of(RubricConstants.SKILL_SPEAKING),
                List.of(content(), PronunciationFluencyTraits.pronunciation(),
                        PronunciationFluencyTraits.oralFluency()),
                Set.of(RubricTraits.CONTENT), null, null);
    }

    private static TraitDefinition content() {
        return CatalogSupport.trait(RubricTraits.CONTENT, "Nội dung", "Content", TraitScale.fixed(CONTENT_MAX),
                TraitSource.AI_LLM, CatalogSupport.levels(
                        "Response is relevant to the prompt but too limited to assign a higher score.",
                        "The response contains content relevant to the situation but does not achieve the primary "
                                + "communication goal, showing a lack of understanding of the situation. "
                                + "Communication is significantly restricted and impedes meaning. OR it minimally "
                                + "addresses the prompt with a situationally appropriate response but no "
                                + "elaboration or support.",
                        "The response contains some content relevant to the situation but does not achieve the "
                                + "primary communication goal or address the specifics of the context. "
                                + "Communication contains restrictions and inaccuracies that compromise meaning or "
                                + "relies heavily on language from the prompt.",
                        "The response partially accomplishes the most basic aspect of the communication goal with "
                                + "some limited consideration of the context. Communication is functional, but the "
                                + "range of expression is limited and contains some inaccuracies.",
                        "The response partially accomplishes the primary communication goal with some "
                                + "consideration of the context, with some omissions or misinterpretations. "
                                + "Communicates adequately, with some limitations of expression.",
                        "The response deals with the situation adequately. It accomplishes the primary "
                                + "communication goal with some consideration of the context, with only minor "
                                + "omissions or misinterpretations. Communicates clearly and accurately with little "
                                + "restriction.",
                        "The response deals with the situation effectively. It accomplishes the primary "
                                + "communication goal with full consideration of the context given in the prompt. "
                                + "Communicates with ease, flexibility, and precision; fully developed, expanding "
                                + "beyond the prompt language to provide a persuasive response."));
    }
}
