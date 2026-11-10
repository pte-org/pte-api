package com.pte.chat.internal.dto.request;

/** One earlier message of the open conversation; {@code role} is "user" or "assistant". */
public record ChatHistoryItem(String role, String text) {
}
