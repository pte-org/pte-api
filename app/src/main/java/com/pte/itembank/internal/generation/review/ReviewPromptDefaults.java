package com.pte.itembank.internal.generation.review;

import com.pte.itembank.PromptDocument.Block;
import com.pte.itembank.internal.generation.prompt.PromptContent;
import com.pte.itembank.internal.generation.prompt.PromptTextAssembler;

import java.util.List;
import java.util.Map;

/**
 * The Reviewer prompt the application ships with, as an editable prompt: the first version stored in the database and
 * the text used when none is stored. The task type and its rubric traits are not part of it: the system puts them in
 * front of the prompt for each question, unless a prompt places {@code {taskType}} or {@code {rubricTraits}} itself.
 */
public final class ReviewPromptDefaults {

    public static final String TASK_TYPE_TOKEN = "{taskType}";
    public static final String RUBRIC_TRAITS_TOKEN = "{rubricTraits}";

    private static final String TITLE = "Reviewer";
    private static final String CHECK_FORMAT = "%s (%s): %s";
    private static final Map<String, String> CHECK_LABELS = Map.of("taskFidelity", "Task fidelity", "cefrFit",
            "CEFR fit", "answerKey", "Answer key", "scorability", "Scorability", "clarity", "Clarity", "safety",
            "Safety");

    private ReviewPromptDefaults() {
    }

    public static PromptContent content() {
        List<String> checks = ReviewPromptContent.CHECKS.stream()
                .map(check -> CHECK_FORMAT.formatted(CHECK_LABELS.getOrDefault(check.key(), check.key()), check.key(),
                        check.question())).toList();
        return new PromptContent(TITLE, List.of(Block.text("", ReviewPromptContent.ROLE)), List.of(
                section("checklist", "Your Review Checklist", Block.list("", checks)),
                section("judging", "What you are judging", Block.list("", ReviewPromptContent.JUDGING)),
                section("output", "Output", Block.text("", ReviewPromptContent.OUTPUT_INSTRUCTION),
                        Block.fields("", ReviewPromptContent.OUTPUT_FIELDS))));
    }

    /** The shipped prompt as the text the model receives, before the tokens are filled in. */
    public static String text() {
        return PromptTextAssembler.assembleWithoutTitle(content());
    }

    private static PromptContent.Section section(String id, String title, Block... blocks) {
        return new PromptContent.Section(id, title, List.of(blocks));
    }
}
