package com.pte.scoring.internal.vendor.gemini;

import com.pte.itembank.PromptTexts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A scoring call is sent with the prompt in use on the AI prompt pages, and says which one went out. */
class StoredPromptModelClientTest {

    private final StructuredModelClient vendor = mock(StructuredModelClient.class);
    private final PromptTexts prompts = mock(PromptTexts.class);
    private final StoredPromptModelClient client = new StoredPromptModelClient(vendor, prompts);

    @Test
    void theStoredVersionInUseReplacesTheCatalogText() {
        UUID version = UUID.randomUUID();
        when(prompts.active(PromptTexts.SCORING_CATEGORY, "WRITE_ESSAY"))
                .thenReturn(Optional.of(new PromptTexts.ActivePrompt("Stored prompt v3", 3, version)));
        when(vendor.generate(any())).thenReturn(response());

        GeminiResponse answer = client.generate(request("WRITE_ESSAY"));

        ArgumentCaptor<GeminiRequest> sent = ArgumentCaptor.forClass(GeminiRequest.class);
        verify(vendor).generate(sent.capture());
        assertThat(sent.getValue().systemInstruction()).isEqualTo("Stored prompt v3");
        assertThat(answer.prompt()).isEqualTo(new PromptUsed("WRITE_ESSAY", PromptUsed.SOURCE_STORED, 3, version,
                StoredPromptModelClient.sha256("Stored prompt v3")));
    }

    @Test
    void withNothingStoredTheCatalogTextGoesOutAndIsSaidSo() {
        when(prompts.active(any(), any())).thenReturn(Optional.empty());
        when(vendor.generate(any())).thenReturn(response());

        GeminiResponse answer = client.generate(request("WRITE_ESSAY"));

        assertThat(answer.prompt().source()).isEqualTo(PromptUsed.SOURCE_CATALOG);
        assertThat(answer.prompt().sha256()).isEqualTo(StoredPromptModelClient.sha256("catalog text"));
    }

    @Test
    void aCallWithoutAPromptKeyIsPassedThrough() {
        when(vendor.generate(any())).thenReturn(response());

        assertThat(client.generate(request(null)).prompt()).isNull();
        verifyNoInteractions(prompts);
    }

    private static GeminiRequest request(String key) {
        return new GeminiRequest("catalog text", "answer", Map.of(), List.of(), null, key);
    }

    private static GeminiResponse response() {
        return new GeminiResponse(JsonMapper.builder().build().readTree("{}"), "m");
    }
}
