package com.aptis.modules.examdelivery.dto;

public record ExtendTimeResponse(
        Long attemptId, String skill, Integer part, Integer addedMinutes, Integer newTotalMinutes) {
}
