package com.pte.itembank.dto.response;

import java.util.UUID;

/** An exam-ready question and the CEFR level it is written for ({@code cefrLevel} is null when unclassified). */
public record ExamCandidateQuestion(UUID publicId, String cefrLevel) {
}
