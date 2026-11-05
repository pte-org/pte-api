package com.pte.scoring.internal.rubric.prompt;

import com.pte.scoring.rubric.LevelDescriptor;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitDefinition;
import com.pte.scoring.rubric.TraitSource;

import java.util.List;

/**
 * The rubric part of an LLM scorer's system instruction, built only from the code catalog: for every trait the model
 * scores (source AI_LLM) its key, its Pearson scale and the Score Guide's description of every level, quoted
 * verbatim. No free text from an item, an answer or an editable rubric version ever goes in here; the caller adds
 * the examiner role before it and the injection guard after it.
 */
public final class RubricPromptBuilder {

    public static final String FORM_IS_CODED = "Form (length, number of sentences, capital letters, punctuation, "
            + "bullet points) is checked by code and is not yours to score; do not let it change any trait below.";

    public static final String SPEECH_IS_MEASURED = "Pronunciation and oral fluency are measured from the recording "
            + "by a speech engine and are not yours to score; judge only what was said, from the transcript.";

    private static final String INSTRUCTION = "Score each trait below as one whole number on its own scale. The "
            + "level descriptions are quoted from the Pearson PTE Academic Score Guide: choose, for each trait, the "
            + "level whose description best matches the response. Never answer with a fraction or a value outside "
            + "the trait's scale. Use each trait's key exactly as the field name.";
    private static final String LEVEL_INDENT = "  ";

    private RubricPromptBuilder() {
    }

    /** The traits the language model scores, in the Score Guide's order. */
    public static List<TraitDefinition> llmTraits(TaskRubric task) {
        return task.traits().stream().filter(trait -> trait.source() == TraitSource.AI_LLM).toList();
    }

    public static String rubricSection(TaskRubric task) {
        StringBuilder section = new StringBuilder(INSTRUCTION).append("\n\n");
        for (TraitDefinition trait : llmTraits(task)) {
            section.append(trait.key()).append(" (").append(trait.displayNameEn()).append("): integer 0-")
                    .append(trait.scale().maxFor(null)).append('\n');
            for (LevelDescriptor level : trait.levels()) {
                section.append(LEVEL_INDENT).append(level.level()).append(": ").append(level.text()).append('\n');
            }
            section.append('\n');
        }
        if (task.formRule() != null) {
            section.append(FORM_IS_CODED).append('\n');
        }
        if (task.traits().stream().anyMatch(trait -> trait.source() == TraitSource.AZURE)) {
            section.append(SPEECH_IS_MEASURED).append('\n');
        }
        return section.toString().strip();
    }
}
