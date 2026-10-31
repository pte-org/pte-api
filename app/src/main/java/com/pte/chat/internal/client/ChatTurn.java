package com.pte.chat.internal.client;

/** One earlier message sent back to the model as context; {@code role} is Gemini's "user" or "model". */
public record ChatTurn(String role, String text) {

    public static ChatTurn user(String text) {
        return new ChatTurn("user", text);
    }

    public static ChatTurn model(String text) {
        return new ChatTurn("model", text);
    }
}
