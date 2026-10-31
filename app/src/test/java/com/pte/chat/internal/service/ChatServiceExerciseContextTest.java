package com.pte.chat.internal.service;

import com.pte.chat.internal.prompt.ChatPrompts;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/** The exercise on screen goes to the provider as context; the question itself is sent as the student typed it. */
class ChatServiceExerciseContextTest extends ChatServiceTestSupport {

    private static final String QUESTION = "How do I say that I use my phone less?";
    private static final String EXERCISE = "Some people believe technology makes us less social. Discuss both views.";

    private ChatSendCommand withExercise(String exercise) {
        return new ChatSendCommand(QUESTION, "WRITE_ESSAY", "en", exercise);
    }

    @Test
    void theProviderReceivesTheExerciseBeforeTheQuestion() {
        service.send(caller, withExercise(EXERCISE));

        verify(client).generate(eq(ChatPrompts.systemInstruction()), any(), eq(ChatPrompts.userTurn(QUESTION, EXERCISE)));
    }

    @Test
    void withoutAnExerciseTheProviderGetsTheQuestionAsBefore() {
        service.send(caller, withExercise(null));

        verify(client).generate(eq(ChatPrompts.systemInstruction()), any(), eq(QUESTION));
    }
}
