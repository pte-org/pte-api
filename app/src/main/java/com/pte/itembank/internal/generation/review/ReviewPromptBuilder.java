package com.pte.itembank.internal.generation.review;

import com.pte.itembank.GenerationRubricTraits;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.internal.generation.draft.AnswerHint;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.prompt.PromptRegistry;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * Builds system and user prompts for the Reviewer role. Context is independent from the Generator:
 * includes task spec, rubric traits, CEFR descriptions, and the draft in delimiters.
 * No generator prompt, reasoning, or intermediate steps appear here.
 */
@Component
public class ReviewPromptBuilder {

    private static final JsonMapper DRAFT_WRITER = JsonMapper.builder().build();

    private static final String CONTEXT_FORMAT = "## Task Type%n%s%n%n## Scoring Rubric Traits%n%s%n%n%s";
    private static final String NO_TRAITS = "This task type has no scoring rubric traits.";

    private final GenerationRubricTraits rubricTraits;
    private final PromptRegistry promptRegistry;

    public ReviewPromptBuilder(GenerationRubricTraits rubricTraits, PromptRegistry promptRegistry) {
        this.rubricTraits = rubricTraits;
        this.promptRegistry = promptRegistry;
    }

    /**
     * Builds the system prompt for review from the prompt in use (the shipped one when none is stored), with the task
     * type and its rubric traits filled in. It does NOT contain the generator's prompt or reasoning.
     */
    public String buildSystemPrompt(String taskTypeKey) {
        return render(promptRegistry.reviewerPrompt().orElseGet(ReviewPromptDefaults::text), taskTypeKey);
    }

    /**
     * A reviewer prompt text with the task type and its rubric traits filled in: where the prompt names them with
     * {@code {taskType}} and {@code {rubricTraits}}, otherwise as a block in front of it.
     */
    public String render(String template, String taskTypeKey) {
        boolean placed = template.contains(ReviewPromptDefaults.TASK_TYPE_TOKEN)
                || template.contains(ReviewPromptDefaults.RUBRIC_TRAITS_TOKEN);
        String traits = traitsText(taskTypeKey);
        String filled = template.replace(ReviewPromptDefaults.TASK_TYPE_TOKEN, taskTypeKey)
                .replace(ReviewPromptDefaults.RUBRIC_TRAITS_TOKEN, traits);
        return placed ? filled : String.format(CONTEXT_FORMAT, taskTypeKey, traits, filled);
    }

    private String traitsText(String taskTypeKey) {
        Optional<GenerationRubricTraits.TaskTraitInfo> rubric = rubricTraits.find(taskTypeKey);
        if (rubric.isEmpty()) {
            return NO_TRAITS;
        }
        StringBuilder sb = new StringBuilder();
        for (GenerationRubricTraits.TraitInfo trait : rubric.get().traits()) {
            int maxScore = trait.maxScore() != null ? trait.maxScore() : 5; // default for per-word
            sb.append(String.format("### %s (0-%d)%n", trait.displayNameEn(), maxScore));
            for (GenerationRubricTraits.LevelDescriptor level : trait.levels()) {
                if (level.text() != null) {
                    sb.append(String.format("- %d: %s%n", level.level(), level.text()));
                }
            }
            sb.append(System.lineSeparator());
        }
        return sb.toString().strip();
    }

    /**
     * Builds the user prompt for review. Includes the draft in delimiters and any fix suggestions from a previous attempt.
     */
    public String buildUserPrompt(ItemDraft draft, CefrLevel cefrLevel, String fixSuggestion) {
        return buildUserPrompt(draft, null, cefrLevel, fixSuggestion);
    }

    /** The user prompt with the item's student hints as data; the reviewer prompt says how to judge them. */
    public String buildUserPrompt(ItemDraft draft, AnswerHint hint, CefrLevel cefrLevel, String fixSuggestion) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Review this %s question for target CEFR %s:%n%n",
            draft.getClass().getSimpleName(), cefrLevel.name()));

        // Draft in delimiters
        sb.append("<<< QUESTION DRAFT >>>\n");
        sb.append(draftToJson(draft)).append("\n");
        sb.append("<<< END DRAFT >>>\n\n");
        if (hint != null && (hint.vi() != null || hint.en() != null)) {
            sb.append("<<< STUDENT HINTS >>>\n");
            sb.append(neutralise(DRAFT_WRITER.writeValueAsString(hint))).append("\n");
            sb.append("<<< END HINTS >>>\n\n");
        }

        if (fixSuggestion != null && !fixSuggestion.isBlank()) {
            sb.append("Previous attempt feedback:\n");
            sb.append(fixSuggestion).append("\n\n");
        }

        sb.append("Provide your verdict and feedback.\n");

        return sb.toString();
    }

    /**
     * The whole draft (passage, options, correct answers, slots, transcript, data...) as JSON: the reviewer must judge
     * the content itself, not a summary of it. The draft markers are neutralised so generated text cannot close the
     * data block and pose as instructions.
     */
    private String draftToJson(ItemDraft draft) {
        return neutralise(DRAFT_WRITER.writeValueAsString(draft));
    }

    private static String neutralise(String json) {
        return json.replace("<<<", "<<<").replace(">>>", ">>>");
    }
}
