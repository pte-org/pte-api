package com.pte.scoring.dto.response;

/** A prompt option and its answer key, for the examiner only. */
public record ExaminerPromptOptionResponse(int orderIndex, String text, Integer blankIndex, boolean correct,
        Integer correctGapIndex) {

    public ExaminerPromptOptionResponse(int orderIndex, String text, Integer blankIndex) {
        this(orderIndex, text, blankIndex, false, null);
    }
}
