package com.pte.chat.internal.client;

/** The two fields the model was asked to return, after validation. */
public record ChatModelOutput(boolean onTopic, String markdown) {
}
