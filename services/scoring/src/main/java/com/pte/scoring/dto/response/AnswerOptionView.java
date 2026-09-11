package com.pte.scoring.dto.response;

/**
 * One option as offered to the student, annotated with whether the student
 * picked it. Always the FULL option set from {@code optionsJson} (not just
 * the picked ones), so a host reviewing the answer sees what was offered,
 * what was correct, and what was picked in one array.
 */
public record AnswerOptionView(Integer orderIndex, String text, boolean correct, boolean selectedByStudent) {
}
