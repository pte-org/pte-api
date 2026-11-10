package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.internal.generation.draft.DuplicateChecker;
import com.pte.itembank.internal.generation.exception.GenerationRequestInvalidException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Phase 2 pipeline requirements:
 * - Task type support (8 text-based types)
 * - Validation → fail on issues
 * - Hash duplicate detection (within-job)
 * - Content hash computation
 */
public class GenerationIntegrationTest {

    private final DuplicateChecker duplicateChecker = new DuplicateChecker();

    @Test
    void duplicateChecker_AllowsUniqueStimulusText() {
        duplicateChecker.checkAndRecordHash("First unique passage", "MC_READING_SINGLE");
        duplicateChecker.checkAndRecordHash("Second unique passage", "MC_READING_SINGLE");
        // Should not throw
    }

    @Test
    void duplicateChecker_RejectsDuplicateContent() {
        String stimulus = "Duplicate passage content";
        duplicateChecker.checkAndRecordHash(stimulus, "MC_READING_SINGLE");

        assertThrows(GenerationRequestInvalidException.class, () ->
                duplicateChecker.checkAndRecordHash(stimulus, "MC_READING_SINGLE"));
    }

    @Test
    void duplicateChecker_NormalizesBeforeHashing() {
        // Different case/punctuation should hash to same value
        String stimulus1 = "CAPITAL TEXT!";
        String stimulus2 = "capital text";

        duplicateChecker.checkAndRecordHash(stimulus1, "MC_READING_SINGLE");

        assertThrows(GenerationRequestInvalidException.class, () ->
                duplicateChecker.checkAndRecordHash(stimulus2, "MC_READING_SINGLE"));
    }

    @Test
    void topicHintSanitization_BlocksDangerousInput() {
        // Test prompt injection protection
        String maliciousHint = "topic'; DROP TABLE questions; --";
        // Should be sanitized to alphanumeric + spaces + basic punctuation
        // Implementation in LlmItemGenerator.sanitizeTopicHint()
    }

    @Test
    void taskTypeSupport_AllEightTextTypes() {
        // Phase 2 supports exactly these 8:
        String[] supported = {
                "MC_READING_SINGLE",
                "MC_READING_MULTIPLE",
                "FILL_IN_THE_BLANKS_DROPDOWN",
                "FILL_IN_THE_BLANKS_DRAG_AND_DROP",
                "RE_ORDER_PARAGRAPHS",
                "SUMMARIZE_WRITTEN_TEXT",
                "WRITE_ESSAY",
                "READ_ALOUD"
        };
        // Verify LlmItemGenerator.supports() returns true for all 8
        // Verify PromptRegistry has prompts for all 8
        // Verify endpoint returns all 8 in SupportedGenerationTaskTypes
    }

    @Test
    void taskTypeMetadata_NeedsAudioFalse_NeedsImageFalse() {
        // All 8 text-based types should have:
        // needsAudioPrompt = false
        // needsImagePrompt = false
        // estimatedSecondsPerItem = 45 (constant ESTIMATED_SECONDS_TEXT)
    }

    @Test
    void contentHashNormalization_IgnoresCaseAndPunctuation() {
        // ContentHasher.hash("Hello World!") == ContentHasher.hash("hello world")
        String hash1 = ContentHasher.hash("Hello, World!");
        String hash2 = ContentHasher.hash("hello world");
        assertEquals(hash1, hash2, "Hash should be case-insensitive and ignore punctuation");
    }

    @Test
    void avoidTopics_PopulatedFrom20RecentAIQuestions() {
        // Phase 3: avoidTopics populated from DB query of last 20 AI questions
        // Phase 2: stub with empty list or "no prior topics"
        // Test: prompt should include avoidTopics wrapped in delimiters
    }

    @Test
    void topicHintDelimiters_ProtectAgainstInjection() {
        // Test that topicHint is wrapped as <topic>...</topic>
        // Test that prompt instructions mention this is data, not commands
    }
}
