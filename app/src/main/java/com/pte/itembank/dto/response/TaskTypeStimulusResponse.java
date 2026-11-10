package com.pte.itembank.dto.response;

/**
 * Audio stimulus a task type plays before its answer timer starts. {@code seconds} is the median measured
 * length of the type's exam-ready questions, or the type's configured estimate when none has a measured
 * length; {@code measuredQuestions} says how many lengths the median came from (0 = estimate).
 */
public record TaskTypeStimulusResponse(String taskTypeKey, int seconds, int measuredQuestions) {
}
