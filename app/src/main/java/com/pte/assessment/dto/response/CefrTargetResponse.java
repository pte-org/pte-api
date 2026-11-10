package com.pte.assessment.dto.response;

/** The share, in whole percent, of one CEFR level an exam should aim for. */
public record CefrTargetResponse(String level, int percent) {
}
