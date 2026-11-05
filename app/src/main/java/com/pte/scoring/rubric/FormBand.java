package com.pte.scoring.rubric;

/** A word-count range (inclusive on both ends) and the Form score it earns. */
public record FormBand(int minWords, int maxWords, int band) {

    boolean contains(int words) {
        return words >= minWords && words <= maxWords;
    }
}
