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

/** Describe Image (Score Guide pp.18-20): Content 0-6 gates Pronunciation and Oral Fluency (p.8). */
public final class DescribeImageRubric {

    private static final String PAGES = "tr.18-20";
    private static final int CONTENT_MAX = 6;

    private DescribeImageRubric() {
    }

    public static TaskRubric create() {
        return new TaskRubric(ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE, Set.of(RubricConstants.SKILL_SPEAKING),
                List.of(content(), PronunciationFluencyTraits.pronunciation(PAGES),
                        PronunciationFluencyTraits.oralFluency(PAGES)),
                Set.of(RubricTraits.CONTENT), null, null, PAGES);
    }

    private static TraitDefinition content() {
        return CatalogSupport.trait(RubricTraits.CONTENT, "Nội dung", "Content", TraitScale.fixed(CONTENT_MAX),
                TraitSource.AI_LLM, "tr.18-19", CatalogSupport.levels(
                        "Response is relevant to the prompt but too limited to assign a higher score.",
                        "The response is composed of disconnected elements, or a list of points identified on the "
                                + "image, without description or elaboration. Vocabulary and expressions are highly "
                                + "restricted. A listener would struggle to visualise the image.",
                        "The response includes minimal, superficial descriptions of the image with some "
                                + "inaccuracies. Limited vocabulary and simple expressions dominate the response. "
                                + "A listener could visualize some elements of the image with effort.",
                        "The response includes mainly superficial descriptions of the image, with minor "
                                + "inaccuracies. The range of expression and vocabulary is narrow and simple "
                                + "expressions are used repeatedly. A listener could visualize elements of the "
                                + "image, but not a cohesive whole.",
                        "The response includes some accurate simple descriptions and basic relationships, but they "
                                + "may not adequately cover all the main features of the image. The range of "
                                + "linguistic expression and vocabulary is sufficient to give basic descriptions "
                                + "with some repetition. A listener could build a basic mental picture.",
                        "The response describes the main features of the image accurately and identifies some "
                                + "relationships without expanding on these in detail. A variety of expressions and "
                                + "vocabulary are used appropriately. A listener could build an accurate mental "
                                + "picture, with minor details missing or misrepresented.",
                        "The response describes the image fully and accurately and expands on the relationships "
                                + "between features of the image to provide a nuanced interpretation. A variety of "
                                + "expressions and vocabulary are used appropriately and with ease and precision. A "
                                + "listener could build a complete mental picture of the image."));
    }
}
