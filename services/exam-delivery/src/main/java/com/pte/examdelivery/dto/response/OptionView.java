package com.pte.examdelivery.dto.response;

/** Student-facing option — deliberately has NO {@code correct} field. */
public record OptionView(String text, int orderIndex) {
}
