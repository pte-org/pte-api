package com.pte.chat.internal.client;

import java.util.List;

/** A single provider exchange; quota accounting and refunds belong to the chat service. */
public interface ChatAiClient {
    ChatAiAnswer generate(String systemInstruction, List<ChatTurn> history, String userMessage);
}
