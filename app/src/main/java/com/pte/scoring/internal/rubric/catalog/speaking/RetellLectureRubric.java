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

/** Re-tell Lecture (Score Guide pp.21-24): Content 0-6 gates Pronunciation and Oral Fluency. */
public final class RetellLectureRubric {

    private static final String PAGES = "tr.21-24";
    private static final int CONTENT_MAX = 6;

    private RetellLectureRubric() {
    }

    public static TaskRubric create() {
        return new TaskRubric(ScoringConstants.TASK_TYPE_RE_TELL_LECTURE,
                Set.of(RubricConstants.SKILL_LISTENING, RubricConstants.SKILL_SPEAKING),
                List.of(content(), PronunciationFluencyTraits.pronunciation(PAGES),
                        PronunciationFluencyTraits.oralFluency(PAGES)),
                Set.of(RubricTraits.CONTENT), null, null, PAGES);
    }

    private static TraitDefinition content() {
        return CatalogSupport.trait(RubricTraits.CONTENT, "Nội dung", "Content", TraitScale.fixed(CONTENT_MAX),
                TraitSource.AI_LLM, "tr.21-23", CatalogSupport.levels(
                        "The response is related to the lecture but is too limited to assign a higher score.",
                        "The response repeats isolated words and phrases from the lecture but does not provide "
                                + "adequate context or meaning. The content appears on topic but does not "
                                + "communicate in any meaningful way. Vocabulary is highly restricted and there is "
                                + "no hierarchy of ideas or coherence among points.",
                        "The response is mostly inaccurate, incomplete, or focuses on non-essential information "
                                + "while missing main ideas, and may rely heavily on repeating language from the "
                                + "lecture. Limited vocabulary and simple expressions dominate. The response lacks "
                                + "coherence and is difficult to follow.",
                        "The response captures some ideas from the lecture but may not capture them fully "
                                + "accurately or differentiate important points from details, reflecting only "
                                + "adequate comprehension. The range of expression is narrow. The response consists "
                                + "mainly of unconnected ideas and requires significant effort to follow.",
                        "The response captures some main ideas of the lecture and some details, but may include a "
                                + "few inaccurate ideas, showing sufficient comprehension. There is an attempt to "
                                + "formulate ideas in own words, with some success. The ideas are not well "
                                + "connected, requiring listeners some effort to follow.",
                        "The response is clear and accurately captures main ideas as well as some important "
                                + "details, reflecting good comprehension. Ideas are formulated in own words, with "
                                + "minor inconsistencies. Connective devices are used appropriately and the "
                                + "response flows generally smoothly.",
                        "The response is clear, accurate, and demonstrates full comprehension of the lecture by "
                                + "paraphrasing the main ideas seamlessly in own words and expanding on important "
                                + "points with specificity. Ideas are well connected in a logical sequence and the "
                                + "response is easy to follow."));
    }
}
