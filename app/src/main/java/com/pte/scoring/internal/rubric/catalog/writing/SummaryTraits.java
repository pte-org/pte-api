package com.pte.scoring.internal.rubric.catalog.writing;

import com.pte.scoring.internal.rubric.catalog.CatalogSupport;
import com.pte.scoring.rubric.RubricTraits;
import com.pte.scoring.rubric.TraitDefinition;
import com.pte.scoring.rubric.TraitScale;
import com.pte.scoring.rubric.TraitSource;

/** Traits that Summarize Written Text and Summarize Spoken Text share word for word in the Score Guide. */
final class SummaryTraits {

    private static final int CONTENT_MAX = 4;
    private static final int TWO_POINT_MAX = 2;

    private SummaryTraits() {
    }

    static TraitDefinition content(String pages) {
        return CatalogSupport.trait(RubricTraits.CONTENT, "Nội dung", "Content", TraitScale.fixed(CONTENT_MAX),
                TraitSource.AI_LLM, pages, CatalogSupport.levels(
                        "Response is too limited to assign a higher score and demonstrates no comprehension of the "
                                + "source text.",
                        "The response is relevant but not meaningfully summarised, demonstrating limited "
                                + "comprehension. It is composed of disconnected ideas or excerpts from the source "
                                + "text without any attempt at synthesis. The response lacks coherence.",
                        "The source text is summarised partially, demonstrating basic comprehension. There is no "
                                + "discernment between main points and peripheral detail, and the response relies "
                                + "heavily on repeating excerpts from the source text. Repetitive or inappropriate "
                                + "connective devices are used.",
                        "The source text is summarised adequately, demonstrating good comprehension. Paraphrasing "
                                + "is used, but not consistently well. Main ideas are correctly identified, with "
                                + "some minor omissions. Ideas are connected, but not synthesized efficiently.",
                        "The source text is summarised comprehensively, demonstrating full comprehension. "
                                + "Paraphrasing is used effectively to communicate a clear and accurate summary and "
                                + "extraneous details are removed. All main ideas are correctly identified and "
                                + "synthesized in a concise and coherent manner."));
    }

    static TraitDefinition grammar(String pages) {
        return CatalogSupport.trait(RubricTraits.GRAMMAR, "Ngữ pháp", "Grammar", TraitScale.fixed(TWO_POINT_MAX),
                TraitSource.AI_LLM, pages, CatalogSupport.levels(
                        "Has defective grammatical structure which could hinder communication.",
                        "Contains grammatical errors but with no hindrance to communication.",
                        "Has correct grammatical structure."));
    }

    static TraitDefinition vocabulary(String pages) {
        return CatalogSupport.trait(RubricTraits.VOCABULARY, "Từ vựng", "Vocabulary", TraitScale.fixed(TWO_POINT_MAX),
                TraitSource.AI_LLM, pages, CatalogSupport.levels(
                        "Has defective word choice which could hinder communication.",
                        "Contains lexical errors but with no hindrances to communication.",
                        "Has appropriate choice of words."));
    }

    static TraitDefinition spelling(String pages) {
        return CatalogSupport.trait(RubricTraits.SPELLING, "Chính tả", "Spelling", TraitScale.fixed(TWO_POINT_MAX),
                TraitSource.AI_LLM, pages, CatalogSupport.levels(
                        "More than one spelling error.", "One spelling error.", "Correct spelling."));
    }
}
