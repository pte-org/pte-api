package com.pte.itembank.internal.generation.llm;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.draft.AnswerHint;
import com.pte.itembank.internal.generation.draft.DraftParser;
import com.pte.itembank.internal.generation.draft.DraftToQuestionMapper;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.exception.GenerationRequestInvalidException;
import com.pte.itembank.internal.generation.pipeline.GenerationCallBudget;
import com.pte.itembank.internal.generation.pipeline.GeneratedItem;
import com.pte.itembank.internal.generation.pipeline.GenerationRequest;
import com.pte.itembank.internal.generation.pipeline.ItemGenerator;
import com.pte.itembank.internal.generation.prompt.PromptRegistry;
import com.pte.itembank.internal.generation.validate.DraftValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Generates question drafts using an LLM via LlmGateway. Calls the generator role,
 * parses JSON response, validates, and retries once on parse failure.
 */
@Component
@ConditionalOnProperty(prefix = "itembank.generation", name = "stub", havingValue = "false", matchIfMissing = true)
public class LlmItemGenerator implements ItemGenerator {

    private final LlmGateway llmGateway;
    private final PromptRegistry promptRegistry;
    private final DraftParser draftParser;
    private final DraftValidator draftValidator;
    private final DraftToQuestionMapper mapper;

    public LlmItemGenerator(
            LlmGateway llmGateway,
            PromptRegistry promptRegistry,
            DraftParser draftParser,
            DraftValidator draftValidator,
            DraftToQuestionMapper mapper) {
        this.llmGateway = llmGateway;
        this.promptRegistry = promptRegistry;
        this.draftParser = draftParser;
        this.draftValidator = draftValidator;
        this.mapper = mapper;
    }

    @Override
    public boolean supports(String taskTypeKey) {
        return promptRegistry.getPrompt(taskTypeKey) != null;
    }

    @Override
    public GeneratedItem generate(GenerationRequest request, GenerationCallBudget budget) {
        budget.consume(); // First call: generation

        // The picture variant of Describe Image has its own writer prompt, draft and mapping.
        String taskTypeKey = request.promptKey();
        String basePrompt = promptRegistry.getPrompt(taskTypeKey);
        String commonPrompt = promptRegistry.getCommonPrompt();

        if (basePrompt == null) {
            throw new GenerationRequestInvalidException(
                    QuestionGenerationConstants.GENERATION_TASK_TYPE_UNSUPPORTED,
                    "Task type not supported");
        }

        // Build user prompt with variable substitution
        String userPrompt = request.feedback() == null || request.feedback().isBlank()
                ? basePrompt
                : basePrompt + "\n\nA reviewer rejected the previous attempt. Address this feedback and return the complete JSON again:\n"
                        + request.feedback();
        String systemPrompt = GeneratorPromptRenderer.render(commonPrompt, request);

        ParsedDraft parsed = callAndParse(systemPrompt, userPrompt, taskTypeKey, budget);
        ItemDraft draft = parsed.draft();
        AnswerHint hint = parsed.hint();

        // One rewrite when the draft breaks structural rules, with the exact rules it broke.
        var issues = draftValidator.validate(draft, taskTypeKey);
        if (!issues.isEmpty() && budget.remaining() > 0) {
            budget.consume();
            String feedback = issues.stream().map(issue -> "- " + issue.message())
                    .collect(java.util.stream.Collectors.joining("\n"));
            parsed = callAndParse(systemPrompt, userPrompt
                    + "\n\nYour previous answer broke these rules. Fix all of them and return the complete JSON again:\n"
                    + feedback, taskTypeKey, budget);
            draft = parsed.draft();
            hint = parsed.hint();
            issues = draftValidator.validate(draft, taskTypeKey);
        }
        if (!issues.isEmpty()) {
            throw new GenerationRequestInvalidException(QuestionGenerationConstants.GENERATION_VALIDATION_FAILED,
                    issues.stream().map(issue -> issue.code()).distinct().limit(6)
                            .collect(java.util.stream.Collectors.joining(", ")));
        }

        // Map draft to CreateQuestionRequest
        var createRequest = mapper.map(draft, request.cefrLevel(), request.pool(), taskTypeKey)
                .withAnswerHint(hint.vi(), hint.en());

        // Extract stimulus text for deduplication
        String stimulusText = extractStimulusText(draft);

        return new GeneratedItem(createRequest, stimulusText, draft.topic(), "LlmItemGenerator", draft);
    }

    /** One provider call and strict parse; a malformed JSON gets one more call asking for valid JSON only. */
    private ParsedDraft callAndParse(String systemPrompt, String userPrompt, String taskTypeKey,
                                   GenerationCallBudget budget) {
        String json = llmGateway.completeJson(LlmRole.GENERATOR, systemPrompt, userPrompt);
        try {
            return parse(json, taskTypeKey);
        } catch (GenerationRequestInvalidException malformed) {
            if (budget.remaining() <= 0) {
                throw malformed;
            }
            budget.consume();
            String again = llmGateway.completeJson(LlmRole.GENERATOR, systemPrompt,
                    userPrompt + "\n\nPlease return valid JSON only, with all required fields.");
            try {
                return parse(again, taskTypeKey);
            } catch (GenerationRequestInvalidException stillMalformed) {
                throw malformed;
            }
        }
    }

    private ParsedDraft parse(String json, String taskTypeKey) {
        return new ParsedDraft(draftParser.parse(json, taskTypeKey), draftParser.parseHint(json));
    }

    private record ParsedDraft(ItemDraft draft, AnswerHint hint) {
    }

    private String extractStimulusText(ItemDraft draft) {
        return switch (draft) {
            case ItemDraft.ChoiceDraft d -> d.passage();
            case ItemDraft.BlankFillDraft d -> d.passageWithSlots();
            case ItemDraft.ReorderDraft d -> String.join(" ", d.paragraphs());
            case ItemDraft.PassageDraft d -> d.mainText();
            case ItemDraft.ListeningDraft d -> d.transcript();
            case ItemDraft.HiwDraft d -> d.transcript();
            case ItemDraft.DialogueDraft d -> d.situation() == null
                    ? d.turns().stream().map(ItemDraft.DialogueDraft.Turn::text).collect(java.util.stream.Collectors.joining(" "))
                    : d.situation();
            case ItemDraft.ChartDraft d -> d.title() + " " + String.join(" ", d.categories()) + " " + d.series();
            case ItemDraft.PictureDraft d -> d.title() + " " + d.imagePrompt();
        };
    }
}
