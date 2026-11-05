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

/** Summarize Group Discussion (Score Guide pp.25-28): Content 0-6 gates Pronunciation and Oral Fluency. */
public final class SummarizeGroupDiscussionRubric {

    private static final String PAGES = "tr.25-28";
    private static final int CONTENT_MAX = 6;

    private SummarizeGroupDiscussionRubric() {
    }

    public static TaskRubric create() {
        return new TaskRubric(ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION,
                Set.of(RubricConstants.SKILL_LISTENING, RubricConstants.SKILL_SPEAKING),
                List.of(content(), PronunciationFluencyTraits.pronunciation(PAGES),
                        PronunciationFluencyTraits.oralFluency(PAGES)),
                Set.of(RubricTraits.CONTENT), null, null, PAGES);
    }

    private static TraitDefinition content() {
        return CatalogSupport.trait(RubricTraits.CONTENT, "Nội dung", "Content", TraitScale.fixed(CONTENT_MAX),
                TraitSource.AI_LLM, "tr.25-27", CatalogSupport.levels(
                        "Response is relevant to the prompt but too limited to assign a higher score.",
                        "The response repeats isolated words and phrases from the discussion but does not provide "
                                + "adequate context or meaning. Vocabulary is highly restricted and there is no "
                                + "hierarchy of ideas or coherence among points.",
                        "The response is mostly inaccurate, incomplete, or focuses on non-essential information "
                                + "from the discussion while missing main ideas. Limited vocabulary and simple "
                                + "expressions dominate. The response lacks coherence and is difficult to follow.",
                        "The response captures some ideas from the discussion but includes some accurate "
                                + "information while missing the substance and general direction of the full "
                                + "conversation. The range of expression is narrow and the response requires "
                                + "effort to follow.",
                        "The response captures the main ideas of the discussion and some details of individual "
                                + "speakers' contributions, but may include a few inaccurate ideas. It focuses more "
                                + "on individual perspectives than on relationships between perspectives and may "
                                + "include basic attempts at comparison.",
                        "The response is clear and accurately captures main ideas as well as some important "
                                + "details of different speakers' contributions. It notes some relationships "
                                + "between points of view and synthesizes perspectives partially. Connective "
                                + "devices are used appropriately.",
                        "The response is clear, accurate, and demonstrates full comprehension of the discussion by "
                                + "paraphrasing the main ideas in own words and expanding on details of each "
                                + "speaker's contribution. It explores relationships between points of view and "
                                + "synthesizes different perspectives effectively."));
    }
}
