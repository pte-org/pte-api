package com.pte.scoring.support;

import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.EssayScoringClient;
import com.pte.scoring.internal.vendor.SpeechScoringClient;
import com.pte.scoring.internal.vendor.openai.OpenAiCompatibleChatClient;
import com.pte.scoring.internal.vendor.openai.OpenAiCompatibleEssayScoringClient;
import com.pte.scoring.internal.vendor.openai.OpenAiCompatibleSpeechScoringClient;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import com.pte.scoring.internal.vendor.stub.StubEssayScoringClient;
import com.pte.scoring.internal.vendor.stub.StubSpeechScoringClient;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;

/**
 * The generic clients of the two providers that have no rubric scorer (decision B of the rubric plan, phase 4):
 * the real {@code stub} clients and the real {@code openai-compatible} clients over a chat client that would hand
 * back an LLM-supplied {@code rawScore} of {@link #LLM_RAW_SCORE}. Every client is a spy, so a test can prove that
 * none of them was ever asked to score a rubric task type.
 */
public record NonRubricProviderClients(String provider, EssayScoringClient essay, SpeechScoringClient speech,
                                       OpenAiCompatibleChatClient chat) {

    public static final int LLM_RAW_SCORE = 95;

    public static List<NonRubricProviderClients> all() {
        return List.of(stub(), openAiCompatible());
    }

    public static NonRubricProviderClients stub() {
        return new NonRubricProviderClients("stub", spy(new StubEssayScoringClient()),
                spy(new StubSpeechScoringClient()), null);
    }

    public static NonRubricProviderClients openAiCompatible() {
        OpenAiCompatibleChatClient chat = mock(OpenAiCompatibleChatClient.class);
        lenient().when(chat.complete(anyString(), anyList())).thenReturn(new AiScoreResult(LLM_RAW_SCORE,
                Map.of("CONTENT", LLM_RAW_SCORE), "llm says excellent", AiProviderCategory.REAL, "OPENAI_COMPATIBLE",
                "test-model", null));
        AiProviderProperties properties = new AiProviderProperties();
        properties.setProvider("openai-compatible");
        properties.setApiKey("test-key-not-secret");
        properties.setEssayModel("test-essay-model");
        properties.setSpeechModel("test-speech-model");
        return new NonRubricProviderClients("openai-compatible",
                spy(new OpenAiCompatibleEssayScoringClient(chat, properties)),
                spy(new OpenAiCompatibleSpeechScoringClient(mock(SpeechAudioLoader.class), chat, properties)), chat);
    }

    public void verifyNeverScored() {
        verify(essay, never()).score(any(), any());
        verify(speech, never()).score(any(), any(), any());
        if (chat != null) {
            verifyNoInteractions(chat);
        }
    }

    @Override
    public String toString() {
        return provider;
    }
}
