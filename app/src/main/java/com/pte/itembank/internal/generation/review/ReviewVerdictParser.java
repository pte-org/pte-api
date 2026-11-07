package com.pte.itembank.internal.generation.review;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.exception.GenerationRequestInvalidException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Parses the reviewer's JSON response. Enforces fail-closed rules:
 * - If verdict is PASS but any check has ok=false, coerce to FAIL.
 * - If estimated CEFR differs 2+ bands from target, FAIL.
 * - If any required check is missing, FAIL.
 */
@Component
public class ReviewVerdictParser {

    private final ObjectMapper objectMapper;

    public ReviewVerdictParser() {
        this.objectMapper = new ObjectMapper();
    }

    public ReviewVerdict parse(String jsonResponse, CefrLevel targetCefr) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            String verdict = root.path("verdict").asText("FAIL").toUpperCase();
            Map<String, ReviewVerdict.CheckResult> checks = parseChecks(root.path("checks"));
            String fixSuggestion = root.path("fixSuggestion").asText("");

            // Fail-closed: if any check is missing or ok=false, make it FAIL
            if (checks.size() < 6 || checks.values().stream().anyMatch(check -> !check.ok())) {
                verdict = "FAIL";
            }

            // Fail-closed: if CEFR is out of range, make it FAIL
            ReviewVerdict candidate = new ReviewVerdict(verdict, checks, fixSuggestion);
            if (candidate.isEstimatedCefrOutOfBands(targetCefr)) {
                verdict = "FAIL";
            }

            return new ReviewVerdict(verdict, checks, fixSuggestion);
        } catch (Exception e) {
            throw new GenerationRequestInvalidException(
                    QuestionGenerationConstants.GENERATION_REVIEW_PARSING_FAILED,
                    "Could not parse reviewer response");
        }
    }

    private Map<String, ReviewVerdict.CheckResult> parseChecks(JsonNode checksNode) {
        Map<String, ReviewVerdict.CheckResult> checks = new HashMap<>();

        for (String checkName : new String[]{"taskFidelity", "cefrFit", "answerKey", "scorability", "clarity", "safety"}) {
            JsonNode checkNode = checksNode.path(checkName);
            boolean ok = checkNode.path("ok").asBoolean(false);
            String note = checkNode.path("note").asText("");
            String estimated = checkNode.path("estimated").asText(null);
            checks.put(checkName, new ReviewVerdict.CheckResult(ok, note, estimated));
        }

        return checks;
    }
}
