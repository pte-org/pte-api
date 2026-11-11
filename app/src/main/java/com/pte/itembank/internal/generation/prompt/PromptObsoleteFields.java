package com.pte.itembank.internal.generation.prompt;

import com.pte.itembank.PromptDocument;
import com.pte.itembank.PromptDocument.Block;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Output fields older prompts still list but no code reads: the task type the job already knows, and option fields of
 * task types that have no options. Some are worse than unused: the essay and read-aloud validators refuse a draft
 * that fills them. They are removed, with the field note of the task type, from the shipped prompts and the live ones.
 */
public final class PromptObsoleteFields {

    private static final Set<String> EVERY_PROMPT = Set.of("taskType");
    /** The field note that described the removed task type field. */
    private static final String TASK_TYPE_NOTE = "Task type (taskType)";
    private static final Set<String> NO_OPTIONS = Set.of("question", "options", "correctIndexes");
    private static final Map<String, Set<String>> BY_PROMPT = Map.of(
            "REPEAT_SENTENCE", NO_OPTIONS,
            "WRITE_FROM_DICTATION", NO_OPTIONS,
            "RE_TELL_LECTURE", NO_OPTIONS,
            "SUMMARIZE_SPOKEN_TEXT", NO_OPTIONS,
            "HIGHLIGHT_INCORRECT_WORDS", NO_OPTIONS,
            "READ_ALOUD", Set.of("keyPoints", "essayPrompt"),
            "WRITE_ESSAY", Set.of("passage", "keyPoints"));

    private PromptObsoleteFields() {
    }

    /** The prompt without the obsolete rows of its output; the same content when it has none. */
    public static PromptContent removedFrom(String promptKey, PromptContent content) {
        Set<String> obsolete = BY_PROMPT.getOrDefault(promptKey, Set.of());
        List<PromptContent.Section> sections = content.sections().stream()
                .map(section -> new PromptContent.Section(section.id(), section.title(), section.blocks().stream()
                        .map(block -> block.type() == PromptDocument.BlockType.FIELDS
                                ? Block.fields(block.label(), block.items().stream()
                                        .filter(item -> !isObsolete(item, obsolete)).toList())
                                : block.type() == PromptDocument.BlockType.LIST
                                        ? Block.list(block.label(), block.items().stream()
                                                .filter(item -> !item.startsWith(TASK_TYPE_NOTE)).toList())
                                        : block)
                        .toList()))
                .toList();
        return new PromptContent(content.title(), content.intro(), sections);
    }

    private static boolean isObsolete(String item, Set<String> obsolete) {
        PromptFieldsJson.Row row = PromptFieldsJson.parseRow(item);
        String name = Objects.requireNonNull(row == null ? "" : row.name());
        return EVERY_PROMPT.contains(name) || obsolete.contains(name);
    }
}
