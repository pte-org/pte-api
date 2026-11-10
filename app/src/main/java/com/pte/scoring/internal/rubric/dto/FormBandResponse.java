package com.pte.scoring.internal.rubric.dto;

/** A word-count range (inclusive) and the Form score it earns. */
public record FormBandResponse(int minWords, int maxWords, int band) {
}
