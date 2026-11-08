package com.pte.chat.internal.prompt;

import com.pte.itembank.PromptTexts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPromptTextsTest {

    private static PromptTexts storing(Map<String, String> texts) {
        return new PromptTexts() {
            @Override
            public Optional<String> activeText(String category, String key) {
                return Optional.ofNullable(texts.get(key));
            }

            @Override
            public void seedIfMissing(String category, String key, String title, List<Part> parts) {
                // Not needed here.
            }
        };
    }

    @Test
    void theStoredInstructionReplacesTheShippedOne() {
        ChatPromptTexts texts = new ChatPromptTexts(storing(Map.of("SYSTEM", "Be brief.")));

        assertThat(texts.systemInstruction()).isEqualTo("Be brief.");
    }

    @Test
    void withNothingStoredTheShippedWordingIsUsed() {
        ChatPromptTexts texts = ChatPromptTexts.builtIn();

        assertThat(texts.systemInstruction()).isEqualTo(ChatPrompts.systemInstruction());
        assertThat(texts.refusal("en", "How do I cook rice?")).isEqualTo(ChatPrompts.refusal("en"));
    }

    @Test
    void aBlankStoredTextFallsBackToTheShippedOne() {
        ChatPromptTexts texts = new ChatPromptTexts(storing(Map.of("SYSTEM", "  ")));

        assertThat(texts.systemInstruction()).isEqualTo(ChatPrompts.systemInstruction());
    }

    @Test
    void aVietnameseQuestionOrPageGetsTheVietnameseReply() {
        ChatPromptTexts texts = new ChatPromptTexts(storing(Map.of("REFUSAL_VI", "Xin lỗi.", "REFUSAL_EN", "Sorry.")));

        assertThat(texts.refusal("en", "Nấu cơm thế nào?")).isEqualTo("Xin lỗi.");
        assertThat(texts.refusal("vi", "How to cook rice")).isEqualTo("Xin lỗi.");
        assertThat(texts.refusal("en", "How to cook rice")).isEqualTo("Sorry.");
    }

    @Test
    void theInstructionIsSplitIntoNamedPartsForTheEditor() {
        List<PromptTexts.Part> parts = ChatPrompts.systemParts();

        assertThat(parts).extracting(PromptTexts.Part::title).contains("Scope", "Out of scope", "Not doing the task",
                "Style", "Output");
        assertThat(parts).allSatisfy(part -> assertThat(part.text()).isNotBlank());
        assertThat(parts.get(0).title()).isEmpty();
        assertThat(parts.get(1).text()).startsWith("Set is_on_topic to true");
    }
}
