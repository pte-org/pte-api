package com.pte.itembank.internal.generation.review;

import com.pte.itembank.internal.generation.llm.LlmGateway;
import com.pte.itembank.internal.generation.llm.LlmProviderException;
import com.pte.itembank.internal.generation.prompt.PromptRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ChartImageReviewerTest {

    private final ChartImageReviewer reviewer = new ChartImageReviewer(mock(LlmGateway.class),
            mock(PromptRegistry.class), JsonMapper.builder().build());

    @Test
    void aPassIgnoresListedIssuesAndAFailKeepsThem() {
        assertThat(reviewer.parse("{\"verdict\":\"PASS\",\"issues\":[\"minor\"]}"))
                .isEqualTo(new ChartImageReviewer.Result(true, List.of()));
        assertThat(reviewer.parse("{\"verdict\":\"fail\",\"issues\":[\"Labels overlap.\", \" \"]}"))
                .isEqualTo(new ChartImageReviewer.Result(false, List.of("Labels overlap.")));
    }

    @Test
    void anAnswerWithoutAVerdictIsRejected() {
        assertThatThrownBy(() -> reviewer.parse("{\"issues\":[]}")).isInstanceOf(LlmProviderException.class);
        assertThatThrownBy(() -> reviewer.parse("not json")).isInstanceOf(LlmProviderException.class);
    }

    @Test
    void theShippedPromptAsksForTheVerdictAndTheIssues() {
        assertThat(ChartImageReviewDefaults.text()).contains("verdict").contains("issues");
    }
}
