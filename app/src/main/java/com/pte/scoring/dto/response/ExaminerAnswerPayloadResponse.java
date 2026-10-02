package com.pte.scoring.dto.response;

import com.pte.scoring.domain.enums.ExaminerAnswerContentKind;

import java.util.List;

/** Student response for blind marking; media IDs and answer-key flags are not serialized. */
public record ExaminerAnswerPayloadResponse(
        ExaminerAnswerContentKind kind,
        String text,
        String mediaUrl,
        List<ExaminerResponseOptionResponse> options,
        List<String> gapValues,
        List<Integer> wordIndices) {
}
