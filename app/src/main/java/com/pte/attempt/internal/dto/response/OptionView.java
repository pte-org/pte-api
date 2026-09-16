package com.pte.attempt.internal.dto.response;

/**
 * Student-facing option — deliberately has NO {@code correct} field.
 * {@code orderIndex} is a decimal string, not a number, so the client never
 * needs to distinguish "stable identity" from "current list position" by
 * type alone.
 */
public record OptionView(String text, String orderIndex) {
}
