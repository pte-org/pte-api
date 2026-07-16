package com.aptis.modules.examdelivery.dto;

public record ProctorActionResponse(Long attemptId, String status, boolean alreadyCompleted) {
}
