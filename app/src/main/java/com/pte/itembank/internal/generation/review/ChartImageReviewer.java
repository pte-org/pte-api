package com.pte.itembank.internal.generation.review;

import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.llm.LlmFailureType;
import com.pte.itembank.internal.generation.llm.LlmGateway;
import com.pte.itembank.internal.generation.llm.LlmProviderException;
import com.pte.itembank.internal.generation.llm.LlmRole;
import com.pte.itembank.internal.generation.prompt.PromptRegistry;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Looks at a drawn chart together with the data it came from and says whether the picture shows that data clearly.
 * Its prompt is the editable {@code CHART_IMAGE_REVIEWER} prompt, or the shipped text while none is stored.
 */
@Component
public class ChartImageReviewer {

    private static final String PASS = "PASS";
    private static final String IMAGE_PNG = "image/png";
    private static final String USER_INTRO = "Chart data (JSON):\n";

    /** The reviewer's answer; {@code issues} is empty when it passed. */
    public record Result(boolean passed, List<String> issues) {
    }

    private final LlmGateway gateway;
    private final PromptRegistry prompts;
    private final JsonMapper json;

    public ChartImageReviewer(LlmGateway gateway, PromptRegistry prompts, JsonMapper json) {
        this.gateway = gateway;
        this.prompts = prompts;
        this.json = json;
    }

    public Result review(ItemDraft.ChartDraft draft, byte[] png) {
        String system = prompts.imageReviewerPrompt().orElseGet(ChartImageReviewDefaults::text);
        String answer = gateway.completeJsonWithImage(LlmRole.REVIEWER, system,
                USER_INTRO + json.writeValueAsString(data(draft)), IMAGE_PNG, png);
        return parse(answer);
    }

    Result parse(String answer) {
        JsonNode root;
        try {
            root = json.readTree(answer);
        } catch (RuntimeException unparsable) {
            throw new LlmProviderException(LlmFailureType.REJECTED);
        }
        if (!root.isObject() || !root.path("verdict").isTextual()) {
            throw new LlmProviderException(LlmFailureType.REJECTED);
        }
        List<String> issues = new ArrayList<>();
        root.path("issues").forEach(issue -> {
            if (issue.isTextual() && !issue.asText().isBlank()) {
                issues.add(issue.asText().strip());
            }
        });
        boolean passed = PASS.equalsIgnoreCase(root.path("verdict").asText().strip());
        return new Result(passed, passed ? List.of() : issues);
    }

    private static Map<String, Object> data(ItemDraft.ChartDraft draft) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chartType", draft.chartType());
        data.put("title", draft.title());
        data.put("xLabel", draft.xLabel());
        data.put("yLabel", draft.yLabel());
        data.put("unit", draft.unit());
        data.put("categories", draft.categories());
        data.put("series", draft.series());
        return data;
    }
}
