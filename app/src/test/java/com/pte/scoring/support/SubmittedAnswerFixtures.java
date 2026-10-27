package com.pte.scoring.support;

import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.itembank.TaskRuntimeProfileDescriptor;

import java.util.UUID;

/** Builds the attempt-side answer view scoring reads, for tests that only care about task type and content. */
public final class SubmittedAnswerFixtures {

    private SubmittedAnswerFixtures() {
    }

    public static SubmittedAnswerView answer(String taskType, String optionsJson, String payload) {
        return answer(taskType, optionsJson, payload, null);
    }

    public static SubmittedAnswerView answer(String taskType, String optionsJson, String payload,
            String correctAnswerText) {
        return answer(taskType, optionsJson, payload, correctAnswerText, null);
    }

    /** {@code runtime} is the item's frozen runtime profile; null leaves routing to the task type. */
    public static SubmittedAnswerView answer(String taskType, String optionsJson, String payload,
            String correctAnswerText, TaskRuntimeProfileDescriptor runtime) {
        return new SubmittedAnswerView(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), taskType, payload, correctAnswerText, optionsJson, false, null,
                null, null, null, null, null, runtime, null, null, null, null);
    }
}
