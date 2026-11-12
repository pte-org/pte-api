package com.pte.scoring.internal.vendor.audioscoring;

import com.pte.itembank.PromptDocument;
import com.pte.itembank.PromptDocument.Block;
import com.pte.itembank.PromptDocument.LevelRow;
import com.pte.itembank.PromptDocument.Section;
import com.pte.scoring.internal.constant.ScoringPromptConstants;
import com.pte.scoring.internal.rubric.prompt.RubricPromptBuilder;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The stored prompts of practice speaking, one per task type under {@code PRACTICE_AUDIO_<TASK>}: what the model that
 * hears the recording is sent. Seeded from here once, then read from the AI prompt pages at every call; the level
 * descriptions and the guard are locked there, so the Pearson levels can never drift from the rubric catalog.
 */
public final class PracticeAudioPrompts {

    private PracticeAudioPrompts() {
    }

    public static Set<String> taskTypes() {
        return AudioTasks.EXAMINERS.keySet();
    }

    public static String keyOf(String taskType) {
        return ScoringPromptConstants.PRACTICE_AUDIO_KEY_PREFIX + taskType;
    }

    /** The task type of a practice speaking prompt key, or empty for any other key. */
    public static Optional<String> taskTypeOf(String key) {
        if (key == null || !key.startsWith(ScoringPromptConstants.PRACTICE_AUDIO_KEY_PREFIX)) {
            return Optional.empty();
        }
        String taskType = key.substring(ScoringPromptConstants.PRACTICE_AUDIO_KEY_PREFIX.length());
        return taskTypes().contains(taskType) ? Optional.of(taskType) : Optional.empty();
    }

    /** The prompt as stored and shown: role, how to listen, the levels, what code counts, feedback, guard. */
    public static PromptDocument document(TaskRubric task) {
        List<Section> sections = new ArrayList<>();
        sections.add(text(ScoringPromptConstants.SECTION_ROLE_ID, ScoringPromptConstants.SECTION_ROLE_TITLE, false,
                AudioTasks.EXAMINERS.get(task.taskType())));
        sections.add(text(ScoringPromptConstants.SECTION_LISTEN_ID, ScoringPromptConstants.SECTION_LISTEN_TITLE,
                false, AudioRubricPrompt.LISTEN));
        List<TraitDefinition> judged = AudioRubricPrompt.judgedTraits(task);
        if (!judged.isEmpty()) {
            List<Block> blocks = new ArrayList<>();
            blocks.add(Block.text("", RubricPromptBuilder.INSTRUCTION));
            judged.forEach(trait -> blocks.add(Block.levels(trait.key() + " (" + trait.displayNameEn() + "): integer 0-"
                    + trait.scale().maxFor(null), trait.levels().stream()
                    .map(level -> new LevelRow(level.level(), level.text())).toList(), true)));
            sections.add(new Section(ScoringPromptConstants.SECTION_LEVELS_ID,
                    ScoringPromptConstants.SECTION_LEVELS_TITLE, true, blocks));
        }
        AudioRubricPrompt.countedNote(task).ifPresent(note -> sections.add(text(
                ScoringPromptConstants.SECTION_NOTES_ID, ScoringPromptConstants.SECTION_NOTES_TITLE, false, note)));
        sections.add(text(ScoringPromptConstants.SECTION_FEEDBACK_ID, ScoringPromptConstants.SECTION_FEEDBACK_TITLE,
                false, AudioRubricPrompt.FEEDBACK_RULES));
        sections.add(text(ScoringPromptConstants.SECTION_GUARD_ID, ScoringPromptConstants.SECTION_GUARD_TITLE, true,
                AudioRubricPrompt.GUARD));
        return new PromptDocument(ScoringPromptConstants.CATEGORY_SCORING, keyOf(task.taskType()),
                keyOf(task.taskType()), sections, AudioRubricPrompt.system(AudioTasks.EXAMINERS.get(task.taskType()), task));
    }

    private static Section text(String id, String title, boolean locked, String body) {
        return new Section(id, title, locked, List.of(Block.text("", body)));
    }
}
