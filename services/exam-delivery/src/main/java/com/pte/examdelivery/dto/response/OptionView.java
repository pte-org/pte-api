package com.pte.examdelivery.dto.response;

/**
 * Student-facing option — deliberately has NO {@code correct} field.
 * {@code orderIndex} is a decimal string, not a number, so the Flutter client
 * never needs to distinguish "stable identity" from "current list position"
 * by type alone (matches {@code AnswerOutboxTable}'s payload convention).
 */
public record OptionView(String text, String orderIndex) {
}
