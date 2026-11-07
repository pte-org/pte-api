package com.pte.itembank.internal.generation.review;

import com.pte.itembank.GenerationRubricTraits;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.internal.generation.draft.ItemDraft;
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

    private final GenerationRubricTraits rubricTraits;

    public ReviewPromptBuilder(GenerationRubricTraits rubricTraits) {
        this.rubricTraits = rubricTraits;
    }

    /**
     * Builds the system prompt for review. The prompt contains task spec, trait details,
     * CEFR descriptions, and does NOT contain the generator's reasoning.
     */
    public String buildSystemPrompt(String taskTypeKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a rigorous question reviewer for PTE Academic.\n\n");

        sb.append("## Task Type\n");
        sb.append(taskTypeKey).append("\n\n");

        // Rubric traits
        Optional<GenerationRubricTraits.TaskTraitInfo> rubric = rubricTraits.find(taskTypeKey);
        if (rubric.isPresent()) {
            sb.append("## Scoring Rubric Traits\n");
            for (GenerationRubricTraits.TraitInfo trait : rubric.get().traits()) {
                int maxScore = trait.maxScore() != null ? trait.maxScore() : 5; // default for per-word
                sb.append(String.format("### %s (0-%d)%n", trait.displayNameEn(), maxScore));
                for (GenerationRubricTraits.LevelDescriptor level : trait.levels()) {
                    if (level.text() != null) {
                        sb.append(String.format("- **%d**: %s%n", level.level(), level.text()));
                    }
                }
                sb.append("\n");
            }
        }

        // Review checklist
        sb.append("## Your Review Checklist\n");
        sb.append("1. **taskFidelity**: Does this question measure the intended construct for this task type?\n");
        sb.append("2. **cefrFit**: Is the linguistic difficulty appropriate for the target CEFR? Provide estimated CEFR level.\n");
        sb.append("3. **answerKey**: Is the answer key clear, unambiguous, and defensible?\n");
        sb.append("4. **scorability**: Can this question be scored by the rubric traits above (or answer-key rules for objective tasks)?\n");
        sb.append("5. **clarity**: Is the question wording clear? Would test-takers understand what is expected?\n");
        sb.append("6. **safety**: Are there any sensitive, offensive, or culturally inappropriate elements?\n\n");

        sb.append("## What you are judging\n");
        sb.append("- Judge only the question content in the draft. The platform scores answers with its own rules, ")
                .append("so do NOT ask for scoring rules, extra fields, or a separate answer-key field.\n");
        sb.append("- Where the answer lives depends on the task type: for Write From Dictation, Repeat Sentence, ")
                .append("Re-tell Lecture and Summarize Spoken Text the transcript is the spoken text and the reference; ")
                .append("for Answer Short Question the answer field lists the accepted answers; for choice tasks ")
                .append("correctIndexes mark the correct options; for blank tasks blanks or slots hold the answers; ")
                .append("for Highlight Incorrect Words changedIndexes are the word positions that were changed in ")
                .append("displayedText.\n");
        sb.append("- A short spoken sentence is normal for dictation and repeat-sentence tasks: judge the level by ")
                .append("vocabulary and structure, not by length.\n");
        sb.append("- Audio and images are produced from the draft after you approve it; do not ask for them.\n");
        sb.append("- Return FAIL only for a real defect in the content (a wrong or ambiguous answer, the wrong level, ")
                .append("unclear wording, unsafe content). Do not fail a question for missing optional metadata.\n\n");

        sb.append("Respond ONLY with valid JSON matching this schema:\n");
        sb.append("{\"verdict\":\"PASS|FAIL\",\"checks\":{");
        sb.append("\"taskFidelity\":{\"ok\":bool,\"note\":\"...\"},");
        sb.append("\"cefrFit\":{\"ok\":bool,\"estimated\":\"B1|B2|C1|C2\",\"note\":\"...\"},");
        sb.append("\"answerKey\":{\"ok\":bool,\"note\":\"...\"},");
        sb.append("\"scorability\":{\"ok\":bool,\"note\":\"...\"},");
        sb.append("\"clarity\":{\"ok\":bool,\"note\":\"...\"},");
        sb.append("\"safety\":{\"ok\":bool,\"note\":\"...\"}},");
        sb.append("\"fixSuggestion\":\"...\"}\n");

        return sb.toString();
    }

    /**
     * Builds the user prompt for review. Includes the draft in delimiters and any fix suggestions from a previous attempt.
     */
    public String buildUserPrompt(ItemDraft draft, CefrLevel cefrLevel, String fixSuggestion) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Review this %s question for target CEFR %s:%n%n",
            draft.getClass().getSimpleName(), cefrLevel.name()));

        // Draft in delimiters
        sb.append("<<< QUESTION DRAFT >>>\n");
        sb.append(draftToJson(draft)).append("\n");
        sb.append("<<< END DRAFT >>>\n\n");

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
        String json = DRAFT_WRITER.writeValueAsString(draft);
        return json.replace("<<<", "<<<").replace(">>>", ">>>");
    }
}
