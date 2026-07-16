package com.aptis.modules.examdelivery.dto;

public record ExamAttemptResponse(Long id, String studentId, Long examId, Boolean isSubmitted, String status) {
}
