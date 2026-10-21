package com.pte.scoring.internal.vendor.azure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceSplitterTest {

    @Test
    void oneChunkGetsTheWholeReference() {
        assertThat(ReferenceSplitter.split(List.of("anything heard"), "  Read this   aloud. "))
                .containsExactly("Read this   aloud.");
    }

    @Test
    void chunksReceiveTheReferenceWordsTheyWereHeardSaying() {
        List<String> parts = ReferenceSplitter.split(List.of("one two three", "four five six"),
                "one two three four five six");

        assertThat(parts).containsExactly("one two three", "four five six");
    }

    @Test
    void aMisheardWordStaysInsideItsChunk() {
        List<String> parts = ReferenceSplitter.split(List.of("one too three", "four five"),
                "one two three four five six");

        assertThat(parts).containsExactly("one two three", "four five six");
    }

    @Test
    void aChunkThatMatchesNothingGetsNoReferenceAndTheNextOneTakesTheRest() {
        List<String> parts = ReferenceSplitter.split(List.of("one two", "zzz", "five six"),
                "one two three four five six");

        assertThat(parts).containsExactly("one two", "", "three four five six");
    }

    @Test
    void referencePunctuationAndCasingSurviveTheSplit() {
        List<String> parts = ReferenceSplitter.split(List.of("hello world", "again"), "Hello, world. Again!");

        assertThat(parts).containsExactly("Hello, world.", "Again!");
    }

    @Test
    void aReferenceWordWithoutLettersDoesNotBreakTheSplit() {
        List<String> parts = ReferenceSplitter.split(List.of("one two", "three"), "one — two three");

        assertThat(parts).hasSize(2);
        assertThat(String.join(" ", parts)).isEqualTo("one — two three");
    }

    @Test
    void nothingHeardLeavesTheWholeReferenceToTheLastChunk() {
        assertThat(ReferenceSplitter.split(List.of("", ""), "one two"))
                .containsExactly("", "one two");
    }
}
