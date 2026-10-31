package com.pte.chat.internal.service;

/** One earlier message of the open conversation as the student's browser sends it; {@code role} is "user" or "assistant". */
public record ChatHistoryEntry(String role, String text) {
}
