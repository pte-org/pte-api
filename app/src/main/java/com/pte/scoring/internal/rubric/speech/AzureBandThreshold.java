package com.pte.scoring.internal.rubric.speech;

/** The lowest Azure score (0-100, inclusive) that earns a Pearson band of Pronunciation or Oral Fluency. */
public record AzureBandThreshold(int band, double minAzureScore) {
}
