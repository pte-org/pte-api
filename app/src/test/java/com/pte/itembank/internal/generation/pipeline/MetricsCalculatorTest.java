package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.internal.generation.dto.GenerationMetaDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricsCalculatorTest {

    private final MetricsCalculator calculator = new MetricsCalculator();

    @Test
    void calculateMetrics_EmptyText_ReturnsZeros() {
        GenerationMetaDto.GenerationMetricsDto metrics = calculator.calculate("");
        assertEquals(0, metrics.words());
        assertEquals(0.0, metrics.avgSentenceLength());
        assertEquals(0.0, metrics.fleschKincaid());
    }

    @Test
    void calculateMetrics_NullText_ReturnsZeros() {
        GenerationMetaDto.GenerationMetricsDto metrics = calculator.calculate(null);
        assertEquals(0, metrics.words());
        assertEquals(0.0, metrics.avgSentenceLength());
        assertEquals(0.0, metrics.fleschKincaid());
    }

    @Test
    void calculateMetrics_SingleWord_ReturnsValidMetrics() {
        GenerationMetaDto.GenerationMetricsDto metrics = calculator.calculate("Hello");
        assertEquals(1, metrics.words());
        assertEquals(1.0, metrics.avgSentenceLength());
        assertTrue(metrics.fleschKincaid() >= 0);
    }

    @Test
    void calculateMetrics_SingleSentence_CalculatesCorrectly() {
        String text = "The quick brown fox jumps over the lazy dog.";
        GenerationMetaDto.GenerationMetricsDto metrics = calculator.calculate(text);
        assertEquals(9, metrics.words());
        assertEquals(9.0, metrics.avgSentenceLength());
        assertTrue(metrics.fleschKincaid() >= 0);
    }

    @Test
    void calculateMetrics_MultipleSentences_CalculatesAverageSentenceLength() {
        String text = "First sentence. Second sentence. Third sentence.";
        GenerationMetaDto.GenerationMetricsDto metrics = calculator.calculate(text);
        assertEquals(6, metrics.words());  // 2 + 2 + 2 words
        assertEquals(2.0, metrics.avgSentenceLength());  // 6 words / 3 sentences
    }

    @Test
    void calculateMetrics_RealPassage_CalculatesMetrics() {
        String passage = "Urban farming has become increasingly popular in recent years. " +
                "Many cities encourage residents to grow vegetables in community gardens. " +
                "This practice reduces food miles and improves community engagement.";
        GenerationMetaDto.GenerationMetricsDto metrics = calculator.calculate(passage);

        assertTrue(metrics.words() > 0);
        assertTrue(metrics.avgSentenceLength() > 0);
        assertTrue(metrics.fleschKincaid() >= 0);
        // Check that metrics are reasonable for academic passage
        assertTrue(metrics.fleschKincaid() <= 20);  // Should not be university-level hard
    }

    @Test
    void calculateMetrics_ExclamationAndQuestion_CountsAllSentences() {
        String text = "What is this? Amazing! Wonderful.";
        GenerationMetaDto.GenerationMetricsDto metrics = calculator.calculate(text);
        assertEquals(5, metrics.words());
        assertEquals(1.7, metrics.avgSentenceLength(), 0.1);  // 5 words / 3 sentences
    }

    @Test
    void calculateMetrics_WhitespaceHandling_CountsWordsCorrectly() {
        String text = "  Multiple   spaces   between   words  ";
        GenerationMetaDto.GenerationMetricsDto metrics = calculator.calculate(text);
        assertEquals(4, metrics.words());
    }

    @Test
    void calculateMetrics_FleschKincaidGrade_RangesReasonably() {
        // Simple text should have low grade
        String simple = "The cat sat. The dog ran.";
        GenerationMetaDto.GenerationMetricsDto simpleMetrics = calculator.calculate(simple);

        // Complex text should have higher grade
        String complex = "The efficacious implementation of multifarious organizational paradigms necessitates " +
                "sophisticated understanding of hermeneutical principles. Expeditious adoption of technology platforms " +
                "facilitates comprehensive integration of methodological frameworks.";
        GenerationMetaDto.GenerationMetricsDto complexMetrics = calculator.calculate(complex);

        assertTrue(complexMetrics.fleschKincaid() > simpleMetrics.fleschKincaid());
    }

    @Test
    void calculateMetrics_WordCount_AccuracyCheck() {
        String text = "One two three four five.";
        GenerationMetaDto.GenerationMetricsDto metrics = calculator.calculate(text);
        assertEquals(5, metrics.words());
    }

    @Test
    void calculateMetrics_SentenceCount_HandlesMultiplePunctuation() {
        String text = "First. Second! Third? Fourth. Fifth!";
        GenerationMetaDto.GenerationMetricsDto metrics = calculator.calculate(text);
        assertEquals(5, metrics.words());
        // Five sentences of one word each
        assertEquals(1.0, metrics.avgSentenceLength(), 0.05);
    }

    @Test
    void calculateMetrics_RoundingPrecision() {
        String text = "word word word word word word word";  // 7 words, 1 sentence
        GenerationMetaDto.GenerationMetricsDto metrics = calculator.calculate(text);
        // Checking rounding to 1 decimal place
        assertTrue(metrics.avgSentenceLength() >= 7.0);
        assertTrue(metrics.avgSentenceLength() <= 7.1);
    }
}
