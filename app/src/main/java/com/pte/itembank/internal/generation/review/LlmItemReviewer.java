package com.pte.itembank.internal.generation.review;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.internal.generation.draft.AnswerHint;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.llm.LlmGateway;
import com.pte.itembank.internal.generation.llm.LlmRole;
import com.pte.itembank.internal.generation.pipeline.GeneratedItem;
import com.pte.itembank.internal.generation.pipeline.GenerationCallBudget;
import org.springframework.stereotype.Component;

/**
 * Reviews a generated draft using the Reviewer role. Temperature is set to 0 for consistency.
 * Context is independent: includes task spec, rubric traits, and CEFR descriptions,
 * but NOT the generator's prompt or reasoning.
 */
@Component
public class LlmItemReviewer {

    private final LlmGateway llmGateway;
    private final ReviewPromptBuilder promptBuilder;
    private final ReviewVerdictParser verdictParser;

    public LlmItemReviewer(LlmGateway llmGateway, ReviewPromptBuilder promptBuilder,
                          ReviewVerdictParser verdictParser) {
        this.llmGateway = llmGateway;
        this.promptBuilder = promptBuilder;
        this.verdictParser = verdictParser;
    }

    /**
     * Reviews the draft and returns a verdict. The budget is consumed for this single call.
     * If a fix suggestion is provided (from a previous failed attempt), it is included in the prompt.
     */
    public ReviewVerdict review(ItemDraft draft, String taskTypeKey, CefrLevel cefrLevel,
                               GenerationCallBudget budget, String fixSuggestion) {
        return review(draft, null, taskTypeKey, cefrLevel, budget, fixSuggestion);
    }

    /** Reviews a generated item together with its answer hints, which must not give the answer away. */
    public ReviewVerdict review(GeneratedItem item, String taskTypeKey, CefrLevel cefrLevel,
                               GenerationCallBudget budget, String fixSuggestion) {
        return review(item.draft(), AnswerHint.of(item.question()), taskTypeKey, cefrLevel, budget, fixSuggestion);
    }

    private ReviewVerdict review(ItemDraft draft, AnswerHint hint, String taskTypeKey, CefrLevel cefrLevel,
                                 GenerationCallBudget budget, String fixSuggestion) {
        budget.consume(); // One review call

        String systemPrompt = promptBuilder.buildSystemPrompt(taskTypeKey);
        String userPrompt = promptBuilder.buildUserPrompt(draft, hint, cefrLevel, fixSuggestion);

        String jsonResponse = llmGateway.completeJson(LlmRole.REVIEWER, systemPrompt, userPrompt);
        return verdictParser.parse(jsonResponse, cefrLevel);
    }
}
