package com.pte.itembank.internal.generation.prompt.version;

import com.pte.itembank.PromptDocument;
import com.pte.itembank.PromptDocument.Block;
import com.pte.itembank.internal.generation.constant.PromptVersionConstants;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.prompt.PromptContent;
import com.pte.itembank.internal.generation.prompt.PromptFieldsJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The parts of a prompt that code depends on and that a proposal may therefore not change: whole sections named here,
 * and in every output section the field names of the reply (their example values stay editable).
 */
public final class PromptLocks {

    private static final Map<AiPromptKind, List<String>> LOCKED_BY_KIND = Map.of(
            AiPromptKind.GENERATOR, List.of("instructions"),
            AiPromptKind.SHARED, List.of("context", "json-format", "answer-hints"),
            // A scoring prompt's level descriptions are the Pearson Score Guide's, the guard keeps answers from
            // steering the score: both are what makes a stored score defensible, so neither can be edited.
            AiPromptKind.SCORER, List.of("levels", "guard"));
    private static final List<String> REVIEWER_LOCKED = List.of("student-hints");

    private PromptLocks() {
    }

    /** Ids of the sections whose content is fixed for a prompt of this kind and key. */
    public static List<String> lockedSectionIds(AiPromptKind kind, String promptKey) {
        if (kind == AiPromptKind.REVIEWER && QuestionGenerationConstants.PROMPT_KEY_REVIEWER.equals(promptKey)) {
            return REVIEWER_LOCKED;
        }
        return LOCKED_BY_KIND.getOrDefault(kind, List.of());
    }

    /** What a proposal changed in the locked parts of the live prompt; empty when it may be saved. */
    public static List<String> problems(AiPromptKind kind, String promptKey, PromptContent live,
                                        PromptContent proposed) {
        List<String> problems = new ArrayList<>();
        for (String id : lockedSectionIds(kind, promptKey)) {
            Optional<PromptContent.Section> before = section(live, id);
            if (before.isPresent() && !before.equals(section(proposed, id))) {
                problems.add(PromptVersionConstants.PROBLEM_LOCKED_SECTION + before.get().title() + ".");
            }
        }
        for (PromptContent.Section before : live.sections()) {
            if (hasFields(before) && !sameOutputShape(before, section(proposed, before.id()).orElse(null))) {
                problems.add(PromptVersionConstants.PROBLEM_OUTPUT_FIELDS + before.title() + ".");
            }
        }
        return problems;
    }

    private static Optional<PromptContent.Section> section(PromptContent content, String id) {
        return content.sections().stream().filter(section -> section.id().equals(id)).findFirst();
    }

    private static boolean hasFields(PromptContent.Section section) {
        return section.blocks().stream().anyMatch(block -> block.type() == PromptDocument.BlockType.FIELDS);
    }

    /** The same text blocks and the same field names in the same order; only field values may differ. */
    private static boolean sameOutputShape(PromptContent.Section before, PromptContent.Section after) {
        if (after == null || before.blocks().size() != after.blocks().size()) {
            return false;
        }
        for (int index = 0; index < before.blocks().size(); index++) {
            Block left = before.blocks().get(index);
            Block right = after.blocks().get(index);
            boolean same = left.type() == PromptDocument.BlockType.FIELDS
                    ? right.type() == left.type() && fieldNames(left).equals(fieldNames(right))
                    : left.equals(right);
            if (!same) {
                return false;
            }
        }
        return true;
    }

    private static List<String> fieldNames(Block block) {
        return block.items().stream().map(PromptFieldsJson::parseRow).filter(Objects::nonNull)
                .map(PromptFieldsJson.Row::name).toList();
    }
}
