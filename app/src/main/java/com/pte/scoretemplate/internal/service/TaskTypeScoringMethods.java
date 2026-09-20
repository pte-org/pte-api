package com.pte.scoretemplate.internal.service;

import com.pte.scoretemplate.domain.enums.ScoringMethod;
import com.pte.scoretemplate.internal.exception.ScoreTemplateValidationException;

import java.util.Map;

/**
 * The scoring method for each of the 22 scored PTE task types is intrinsic to
 * the task type itself (per the APEUni V5 table) — never a per-template
 * choice. Derived here instead of accepted as admin input on purpose: an
 * admin picking the wrong {@code ScoringMethod} for a task type would
 * silently misroute scoring in {@code scoring.ScoringMethodResolver}/
 * {@code AiScoringWorker} (e.g. an AI_SPEECH task routed to OBJECTIVE).
 * Duplicated (not sourced from {@code itembank.PteTaskType}) for the same
 * module-boundary reason as {@link ScoreTemplateActivationValidator}'s
 * {@code REQUIRED_TASK_TYPES}.
 */
final class TaskTypeScoringMethods {

    private static final Map<String, ScoringMethod> BY_TASK_TYPE = Map.ofEntries(
            Map.entry("READ_ALOUD", ScoringMethod.AI_SPEECH),
            Map.entry("REPEAT_SENTENCE", ScoringMethod.AI_SPEECH),
            Map.entry("DESCRIBE_IMAGE", ScoringMethod.AI_SPEECH),
            Map.entry("RE_TELL_LECTURE", ScoringMethod.AI_SPEECH),
            Map.entry("ANSWER_SHORT_QUESTION", ScoringMethod.AI_SPEECH),
            Map.entry("SUMMARIZE_GROUP_DISCUSSION", ScoringMethod.AI_SPEECH),
            Map.entry("RESPOND_TO_A_SITUATION", ScoringMethod.AI_SPEECH),
            Map.entry("SUMMARIZE_WRITTEN_TEXT", ScoringMethod.AI_TEXT),
            Map.entry("WRITE_ESSAY", ScoringMethod.AI_TEXT),
            Map.entry("FILL_IN_THE_BLANKS_DROPDOWN", ScoringMethod.OBJECTIVE),
            Map.entry("MC_READING_MULTIPLE", ScoringMethod.OBJECTIVE),
            Map.entry("RE_ORDER_PARAGRAPHS", ScoringMethod.OBJECTIVE),
            Map.entry("FILL_IN_THE_BLANKS_DRAG_AND_DROP", ScoringMethod.OBJECTIVE),
            Map.entry("MC_READING_SINGLE", ScoringMethod.OBJECTIVE),
            Map.entry("SUMMARIZE_SPOKEN_TEXT", ScoringMethod.AI_TEXT),
            Map.entry("MC_LISTENING_MULTIPLE", ScoringMethod.OBJECTIVE),
            Map.entry("FILL_IN_THE_BLANKS_TYPE_IN", ScoringMethod.OBJECTIVE),
            Map.entry("HIGHLIGHT_CORRECT_SUMMARY", ScoringMethod.OBJECTIVE),
            Map.entry("MC_LISTENING_SINGLE", ScoringMethod.OBJECTIVE),
            Map.entry("SELECT_MISSING_WORD", ScoringMethod.OBJECTIVE),
            Map.entry("HIGHLIGHT_INCORRECT_WORDS", ScoringMethod.OBJECTIVE),
            Map.entry("WRITE_FROM_DICTATION", ScoringMethod.OBJECTIVE));

    private TaskTypeScoringMethods() {
    }

    static ScoringMethod resolve(String taskType) {
        ScoringMethod method = BY_TASK_TYPE.get(taskType);
        if (method == null) {
            throw new ScoreTemplateValidationException("Unknown task type '" + taskType + "' has no defined scoring method");
        }
        return method;
    }
}
