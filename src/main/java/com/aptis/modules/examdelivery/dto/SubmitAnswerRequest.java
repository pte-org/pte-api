package com.aptis.modules.examdelivery.dto;

public record SubmitAnswerRequest(
    Long questionId,
    String questionType,
    String content
) {}
