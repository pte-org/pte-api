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

/** Write Essay (Score Guide pp.34-39): seven traits worth 26 points; Content or Form 0 zeroes the essay. */
public final class WriteEssayRubric {
    private static final int SIX = 6;
    private static final int TWO = 2;

    private WriteEssayRubric() {
    }

    public static TaskRubric create() {
        return new TaskRubric(ScoringConstants.TASK_TYPE_WRITE_ESSAY, Set.of(RubricConstants.SKILL_WRITING),
                List.of(
                        llm(RubricTraits.CONTENT, "Nội dung", "Content", SIX, WriteEssayLevels.content()),
                        CatalogSupport.trait(RubricTraits.FORM, "Hình thức", "Form", TraitScale.fixed(TWO),
                                TraitSource.MECHANICAL, WriteEssayLevels.form()),
                        llm(RubricTraits.DEVELOPMENT_STRUCTURE_COHERENCE, "Phát triển, cấu trúc và mạch lạc",
                                "Development, Structure and Coherence", SIX,
                                WriteEssayLevels.developmentStructureCoherence()),
                        llm(RubricTraits.GRAMMAR, "Ngữ pháp", "Grammar", TWO, WriteEssayLevels.grammar()),
                        llm(RubricTraits.GENERAL_LINGUISTIC_RANGE, "Phạm vi ngôn ngữ chung",
                                "General Linguistic Range", SIX, WriteEssayLevels.generalLinguisticRange()),
                        llm(RubricTraits.VOCABULARY_RANGE, "Phạm vi từ vựng", "Vocabulary Range", TWO,
                                WriteEssayLevels.vocabularyRange()),
                        llm(RubricTraits.SPELLING, "Chính tả", "Spelling", TWO, WriteEssayLevels.spelling())),
                Set.of(RubricTraits.CONTENT, RubricTraits.FORM),
                new FormRule(List.of(new FormBand(200, 300, 2), new FormBand(120, 199, 1), new FormBand(301, 380, 1)),
                        false, true), null);
    }

    private static TraitDefinition llm(String key, String vi, String en, int max,
                                       List<com.pte.scoring.rubric.LevelDescriptor> levels) {
        return CatalogSupport.trait(key, vi, en, TraitScale.fixed(max), TraitSource.AI_LLM, levels);
    }
}
