package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.dto.request.OptionRequest;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deterministic generator that never calls a provider, so the job machinery can be exercised end to end. It only
 * exists in the test, local and local-schema profiles and only when {@code itembank.generation.stub=true}; the
 * startup validator rejects the flag anywhere else.
 */
@Component
@Profile({"test", "local", "local-schema"})
@ConditionalOnProperty(prefix = "itembank.generation", name = "stub", havingValue = "true")
public class StubItemGenerator implements ItemGenerator {

    private static final String TOPIC = "stub topic ";
    private static final String CORRECT_OPTION = "The first statement";
    private static final List<String> WRONG_OPTIONS = List.of("The second statement", "The third statement",
            "The fourth statement");

    @Override
    public boolean supports(String taskTypeKey) {
        return QuestionGenerationConstants.STUB_TASK_TYPE.equals(taskTypeKey);
    }

    @Override
    public GeneratedItem generate(GenerationRequest request, GenerationCallBudget budget) {
        String topic = TOPIC + request.itemIndex();
        // The job id makes every passage unique, so the duplicate check never fires on stub output.
        String passage = "Stub passage " + request.jobPublicId() + " item " + request.itemIndex()
                + ": the committee reviewed the proposal and agreed to publish the first statement.";
        String promptText = passage + "\n\nWhich statement does the passage support?";
        List<OptionRequest> options = new java.util.ArrayList<>();
        options.add(new OptionRequest(CORRECT_OPTION, true, 0));
        for (int index = 0; index < WRONG_OPTIONS.size(); index++) {
            options.add(new OptionRequest(WRONG_OPTIONS.get(index), false, index + 1));
        }
        CreateQuestionRequest question = new CreateQuestionRequest(PteTaskType.MC_READING_SINGLE.name(),
                request.taskTypeKey(), promptText, null, null, null, null, null, null, options,
                request.pool().name(), null, null, request.cefrLevel().name());
        return new GeneratedItem(question, passage, topic, QuestionGenerationConstants.STUB_GENERATOR_NAME);
    }
}
