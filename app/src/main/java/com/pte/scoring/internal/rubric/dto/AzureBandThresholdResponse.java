package com.pte.scoring.internal.rubric.dto;

/** The lowest Azure score (0-100, inclusive) that earns a band of Pronunciation or Oral Fluency. */
public record AzureBandThresholdResponse(int band, double minAzureScore) {
}
