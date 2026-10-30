package com.pte.chat.internal.service;

import com.pte.chat.internal.prompt.ChatPrompts;
import com.pte.chat.internal.service.cache.ChatCacheKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/** The exercise on screen goes to the provider as context; what is stored and who shares an answer stay exact. */
class ChatServiceExerciseContextTest extends ChatServiceTestSupport {

    private static final String QUESTION = "How do I say that I use my phone less?";
    private static final String EXERCISE = "Some people believe technology makes us less social. Discuss both views.";

    private ChatSendCommand withExercise(String exercise) {
        return new ChatSendCommand(command(QUESTION).sessionId(), QUESTION, "WRITE_ESSAY", "en", exercise);
    }

    @Test
    void theProviderReceivesTheExerciseBeforeTheQuestion() {
        service.send(caller, withExercise(EXERCISE));

        verify(client).generate(eq(ChatPrompts.systemInstruction()), any(), eq(ChatPrompts.userTurn(QUESTION, EXERCISE)));
    }

    @Test
    void theStoredQuestionIsTheStudentsQuestionWithoutTheExercise() {
        service.send(caller, withExercise(EXERCISE));

        assertThat(savedMessage().getQuestion()).isEqualTo(QUESTION);
    }

    @Test
    void withoutAnExerciseTheProviderGetsTheQuestionAsBefore() {
        service.send(caller, withExercise(null));

        verify(client).generate(eq(ChatPrompts.systemInstruction()), any(), eq(QUESTION));
    }

    @Test
    void theSharedCacheIsLookedUpPerExercise() {
        service.send(caller, withExercise(EXERCISE));

        verify(cacheService).find(ChatCacheKey.of(QUESTION, "en", "WRITE_ESSAY", ChatPrompts.VERSION, EXERCISE));
    }
}
