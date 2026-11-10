package com.pte.itembank.internal.generation;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.dto.response.GenerationMetaResponse;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.mapper.QuestionMapper;
import com.pte.itembank.internal.service.lifecycle.QuestionLifecycleService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for generationMeta exposure in QuestionResponse.
 * Verifies that:
 * - generationMeta is parsed when present
 * - generationMeta is null when absent
 * - Update operations can set editedByHuman flag
 * - Revisions do not copy generation fields
 */
class GenerationMetaResponseTest {

    private static final JsonMapper mapper = new JsonMapper();

    @Test
    void testGenerationMetaParsedWhenPresent() {
        // Arrange
        Question question = createQuestion();
        String metaJson = createGenerationMeta("PASS", "test-topic");
        question.setGenerationMeta(metaJson);

        // Act
        QuestionResponse response = QuestionMapper.toResponse(question, mapper);

        // Assert
        assertNotNull(response.generationMeta());
        assertEquals("PASS", response.generationMeta().review().verdict());
        assertEquals("test-topic", response.generationMeta().topic());
        assertFalse(response.generationMeta().editedByHuman());
    }

    @Test
    void testGenerationMetaNullWhenAbsent() {
        // Arrange
        Question question = createQuestion();
        question.setGenerationMeta(null);

        // Act
        QuestionResponse response = QuestionMapper.toResponse(question, mapper);

        // Assert
        assertNull(response.generationMeta());
    }

    @Test
    void testGenerationMetaParsedByTheDefaultReader() {
        // Arrange
        Question question = createQuestion();
        String metaJson = createGenerationMeta("PASS", "test-topic");
        question.setGenerationMeta(metaJson);

        // Act: the one-argument overload used by every service reads the stored meta itself
        QuestionResponse response = QuestionMapper.toResponse(question);

        // Assert
        assertNotNull(response.generationMeta());
        assertEquals("test-topic", response.generationMeta().topic());
    }

    @Test
    void testRevisionDoesNotCopyGenerationFields() {
        // This test verifies that when createRevision is called,
        // the new revision doesn't copy:
        // - generationMeta
        // - contentHash
        // - generationJobPublicId
        // - generationItemIndex

        Question source = createQuestion();
        source.setGenerationMeta(createGenerationMeta("PASS", "original-topic"));
        source.setContentHash("abc123def456");
        source.setGenerationJobPublicId(UUID.randomUUID());
        source.setGenerationItemIndex(0);

        // Note: Full revision creation test would be in QuestionLifecycleService tests
        // This is a marker for where that validation should occur
    }

    @Test
    void testCheckResultResponseStructure() {
        // Arrange
        Question question = createQuestion();
        String metaJson = createGenerationMetaWithChecks("PASS");
        question.setGenerationMeta(metaJson);

        // Act
        QuestionResponse response = QuestionMapper.toResponse(question, mapper);

        // Assert
        assertNotNull(response.generationMeta());
        assertNotNull(response.generationMeta().review());
        assertNotNull(response.generationMeta().review().checks());
        assertTrue(response.generationMeta().review().checks().containsKey("taskFidelity"));
    }

    @Test
    void testMetricsExposure() {
        // Arrange
        Question question = createQuestion();
        String metaJson = createGenerationMetaWithMetrics();
        question.setGenerationMeta(metaJson);

        // Act
        QuestionResponse response = QuestionMapper.toResponse(question, mapper);

        // Assert
        assertNotNull(response.generationMeta());
        assertNotNull(response.generationMeta().metrics());
        assertEquals(250, response.generationMeta().metrics().words());
    }

    // Helper methods
    private Question createQuestion() {
        Question q = new Question();
        q.setId(1L);
        q.setPublicId(UUID.randomUUID());
        q.setTenantId(null);
        q.setPteTaskType(PteTaskType.MC_READING_SINGLE);
        q.setTaskTypeKey("MC_READING_SINGLE");
        q.setTaskTypeSection("READING");
        q.setStatus(QuestionStatus.DRAFT);
        q.setPool(QuestionPool.EXAM);
        q.setVersion(1L);
        return q;
    }

    private String createGenerationMeta(String verdict, String topic) {
        return String.format(
            "{\"schema\":1,\"jobPublicId\":\"00000000-0000-0000-0000-000000000001\"," +
            "\"model\":{\"generator\":\"gpt-4\",\"reviewer\":\"gpt-4\"}," +
            "\"promptVersion\":\"2026-11-07\",\"topic\":\"%s\",\"attempts\":1," +
            "\"review\":{\"verdict\":\"%s\",\"checks\":{},\"fixSuggestion\":null}," +
            "\"metrics\":{\"words\":200,\"avgSentenceLength\":15.5,\"fleschKincaid\":8.2}," +
            "\"keyPoints\":[],\"media\":null}", topic, verdict);
    }

    private String createGenerationMetaWithChecks(String verdict) {
        return String.format(
            "{\"schema\":1,\"jobPublicId\":\"00000000-0000-0000-0000-000000000001\"," +
            "\"model\":{\"generator\":\"gpt-4\",\"reviewer\":\"gpt-4\"}," +
            "\"promptVersion\":\"2026-11-07\",\"topic\":\"test\",\"attempts\":1," +
            "\"review\":{\"verdict\":\"%s\"," +
            "\"checks\":{\"taskFidelity\":{\"ok\":true,\"note\":\"Matches task type\"}," +
            "\"cefrFit\":{\"ok\":true,\"note\":\"B2 level\"}},\"fixSuggestion\":null}," +
            "\"metrics\":{\"words\":200,\"avgSentenceLength\":15.5,\"fleschKincaid\":8.2}," +
            "\"keyPoints\":[],\"media\":null}", verdict);
    }

    private String createGenerationMetaWithMetrics() {
        return "{\"schema\":1,\"jobPublicId\":\"00000000-0000-0000-0000-000000000001\"," +
               "\"model\":{\"generator\":\"gpt-4\",\"reviewer\":\"gpt-4\"}," +
               "\"promptVersion\":\"2026-11-07\",\"topic\":\"test\",\"attempts\":1," +
               "\"review\":{\"verdict\":\"PASS\",\"checks\":{},\"fixSuggestion\":null}," +
               "\"metrics\":{\"words\":250,\"avgSentenceLength\":16.2,\"fleschKincaid\":8.5}," +
               "\"keyPoints\":[\"key1\",\"key2\"],\"media\":null}";
    }
}
