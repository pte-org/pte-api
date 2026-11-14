package com.pte.itembank.internal.generation.review;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.exception.GenerationRequestInvalidException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReviewVerdictParserTest {

    private final ReviewVerdictParser parser = new ReviewVerdictParser();

    @Test
    void parse_ValidPASSVerdict_ReturnsPASS() {
        String json = """
                {
                  "verdict": "PASS",
                  "checks": {
                    "taskFidelity": {"ok": true, "note": "Good"},
                    "cefrFit": {"ok": true, "estimated": "B2", "note": "Appropriate"},
                    "answerKey": {"ok": true, "note": "Clear"},
                    "scorability": {"ok": true, "note": "Measurable"},
                    "clarity": {"ok": true, "note": "Clear wording"},
                    "safety": {"ok": true, "note": "No issues"}
                  },
                  "fixSuggestion": ""
                }
                """;
        ReviewVerdict verdict = parser.parse(json, CefrLevel.B2);
        assertTrue(verdict.isPassed());
        assertEquals("PASS", verdict.verdict());
    }

    @Test
    void parse_PASSWithOneFailedCheck_CoerceToFAIL() {
        String json = """
                {
                  "verdict": "PASS",
                  "checks": {
                    "taskFidelity": {"ok": false, "note": "Not task-aligned"},
                    "cefrFit": {"ok": true, "estimated": "B2", "note": "Appropriate"},
                    "answerKey": {"ok": true, "note": "Clear"},
                    "scorability": {"ok": true, "note": "Measurable"},
                    "clarity": {"ok": true, "note": "Clear"},
                    "safety": {"ok": true, "note": "No issues"}
                  },
                  "fixSuggestion": "Fix the task alignment"
                }
                """;
        ReviewVerdict verdict = parser.parse(json, CefrLevel.B2);
        assertFalse(verdict.isPassed());
        assertEquals("FAIL", verdict.verdict());
    }

    @Test
    void parse_FAILVerdict_ReturnsFAIL() {
        String json = """
                {
                  "verdict": "FAIL",
                  "checks": {
                    "taskFidelity": {"ok": false, "note": "Misaligned"},
                    "cefrFit": {"ok": true, "estimated": "B2", "note": "OK"},
                    "answerKey": {"ok": true, "note": "Clear"},
                    "scorability": {"ok": true, "note": "Measurable"},
                    "clarity": {"ok": true, "note": "Clear"},
                    "safety": {"ok": true, "note": "No issues"}
                  },
                  "fixSuggestion": "Rewrite with better alignment"
                }
                """;
        ReviewVerdict verdict = parser.parse(json, CefrLevel.B2);
        assertFalse(verdict.isPassed());
        assertEquals("FAIL", verdict.verdict());
    }

    @Test
    void parse_FAILWithEveryCheckPassed_IsPASSWithTheSuggestionKept() {
        String json = """
                {
                  "verdict": "FAIL",
                  "checks": {
                    "taskFidelity": {"ok": true, "note": "OK"},
                    "cefrFit": {"ok": true, "estimated": "C2", "note": "OK"},
                    "answerKey": {"ok": true, "note": "Clear"},
                    "scorability": {"ok": true, "note": "Measurable"},
                    "clarity": {"ok": true, "note": "Clear"},
                    "safety": {"ok": true, "note": "No issues"}
                  },
                  "fixSuggestion": "Consider adding a fourth series"
                }
                """;
        ReviewVerdict verdict = parser.parse(json, CefrLevel.C2);
        assertTrue(verdict.isPassed());
        assertEquals("Consider adding a fourth series", verdict.fixSuggestion());
    }

    @Test
    void parse_CefrOutOfRange_TwoPlus_CoerceToFAIL() {
        // Target B2, estimated C1 (2 bands higher)
        String json = """
                {
                  "verdict": "PASS",
                  "checks": {
                    "taskFidelity": {"ok": true, "note": "Good"},
                    "cefrFit": {"ok": true, "estimated": "C2", "note": "Too hard"},
                    "answerKey": {"ok": true, "note": "Clear"},
                    "scorability": {"ok": true, "note": "Measurable"},
                    "clarity": {"ok": true, "note": "Clear"},
                    "safety": {"ok": true, "note": "No issues"}
                  },
                  "fixSuggestion": ""
                }
                """;
        ReviewVerdict verdict = parser.parse(json, CefrLevel.B2);
        assertFalse(verdict.isPassed());
        assertEquals("FAIL", verdict.verdict());
    }

    @Test
    void parse_CefrOutOfRange_OneBand_StillPASS() {
        // Target B2, estimated B1 (1 band lower) - should still pass
        String json = """
                {
                  "verdict": "PASS",
                  "checks": {
                    "taskFidelity": {"ok": true, "note": "Good"},
                    "cefrFit": {"ok": true, "estimated": "B1", "note": "Slightly easy"},
                    "answerKey": {"ok": true, "note": "Clear"},
                    "scorability": {"ok": true, "note": "Measurable"},
                    "clarity": {"ok": true, "note": "Clear"},
                    "safety": {"ok": true, "note": "No issues"}
                  },
                  "fixSuggestion": ""
                }
                """;
        ReviewVerdict verdict = parser.parse(json, CefrLevel.B2);
        assertTrue(verdict.isPassed());
    }

    @Test
    void parse_MissingCheck_CoerceToFAIL() {
        String json = """
                {
                  "verdict": "PASS",
                  "checks": {
                    "taskFidelity": {"ok": true, "note": "Good"},
                    "cefrFit": {"ok": true, "estimated": "B2", "note": "OK"},
                    "answerKey": {"ok": true, "note": "Clear"}
                  },
                  "fixSuggestion": ""
                }
                """;
        ReviewVerdict verdict = parser.parse(json, CefrLevel.B2);
        assertFalse(verdict.isPassed());
        assertEquals("FAIL", verdict.verdict());
    }

    @Test
    void parse_InvalidJson_ThrowsException() {
        String invalidJson = "{ invalid json ]";
        assertThrows(GenerationRequestInvalidException.class, () -> {
            parser.parse(invalidJson, CefrLevel.B2);
        });
    }

    @Test
    void parse_MissingVerdictField_DefaultsFAIL() {
        String json = """
                {
                  "checks": {
                    "taskFidelity": {"ok": true, "note": "Good"},
                    "cefrFit": {"ok": true, "estimated": "B2", "note": "OK"},
                    "answerKey": {"ok": true, "note": "Clear"},
                    "scorability": {"ok": true, "note": "Measurable"},
                    "clarity": {"ok": true, "note": "Clear"},
                    "safety": {"ok": true, "note": "No issues"}
                  },
                  "fixSuggestion": ""
                }
                """;
        ReviewVerdict verdict = parser.parse(json, CefrLevel.B2);
        assertFalse(verdict.isPassed());
    }

    @Test
    void parse_PreservesFixSuggestion() {
        String json = """
                {
                  "verdict": "FAIL",
                  "checks": {
                    "taskFidelity": {"ok": false, "note": "Bad"},
                    "cefrFit": {"ok": true, "estimated": "B2", "note": "OK"},
                    "answerKey": {"ok": true, "note": "Clear"},
                    "scorability": {"ok": true, "note": "Measurable"},
                    "clarity": {"ok": true, "note": "Clear"},
                    "safety": {"ok": true, "note": "No issues"}
                  },
                  "fixSuggestion": "Please improve task alignment"
                }
                """;
        ReviewVerdict verdict = parser.parse(json, CefrLevel.B2);
        assertEquals("Please improve task alignment", verdict.fixSuggestion());
    }

    @Test
    void parse_CaseSensitivity_NormalizedToUppercase() {
        String json = """
                {
                  "verdict": "pass",
                  "checks": {
                    "taskFidelity": {"ok": true, "note": "Good"},
                    "cefrFit": {"ok": true, "estimated": "B2", "note": "OK"},
                    "answerKey": {"ok": true, "note": "Clear"},
                    "scorability": {"ok": true, "note": "Measurable"},
                    "clarity": {"ok": true, "note": "Clear"},
                    "safety": {"ok": true, "note": "No issues"}
                  },
                  "fixSuggestion": ""
                }
                """;
        ReviewVerdict verdict = parser.parse(json, CefrLevel.B2);
        assertEquals("PASS", verdict.verdict());
    }

    @Test
    void parse_AllChecksHaveCorrectStructure() {
        String json = """
                {
                  "verdict": "PASS",
                  "checks": {
                    "taskFidelity": {"ok": true, "note": "Note1"},
                    "cefrFit": {"ok": true, "estimated": "B2", "note": "Note2"},
                    "answerKey": {"ok": true, "note": "Note3"},
                    "scorability": {"ok": true, "note": "Note4"},
                    "clarity": {"ok": true, "note": "Note5"},
                    "safety": {"ok": true, "note": "Note6"}
                  },
                  "fixSuggestion": ""
                }
                """;
        ReviewVerdict verdict = parser.parse(json, CefrLevel.B2);
        assertEquals(6, verdict.checks().size());
        assertTrue(verdict.checks().containsKey("taskFidelity"));
        assertTrue(verdict.checks().containsKey("safety"));
    }
}
