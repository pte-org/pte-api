package com.pte.scoring.internal.config;

import com.pte.media.MediaService;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiAnswerScorer;
import com.pte.scoring.internal.vendor.SpeechScoringClient;
import com.pte.scoring.internal.vendor.azure.AzureSpeechAssessor;
import com.pte.scoring.internal.vendor.azure.AzureSpeechRestClient;
import com.pte.scoring.internal.vendor.azure.ReadAloudScorer;
import com.pte.scoring.internal.vendor.azure.RepeatSentenceScorer;
import com.pte.scoring.internal.vendor.azure.ShortQuestionScorer;
import com.pte.scoring.internal.vendor.gemini.scorer.DescribeImageScorer;
import com.pte.scoring.internal.vendor.gemini.PromptImageLoader;
import com.pte.scoring.internal.vendor.gemini.scorer.RespondToSituationScorer;
import com.pte.scoring.internal.vendor.gemini.scorer.RetellLectureScorer;
import com.pte.scoring.internal.vendor.gemini.scorer.SummarizeGroupDiscussionScorer;
import com.pte.scoring.internal.vendor.gemini.scorer.SummarizeSpokenTextScorer;
import com.pte.scoring.internal.vendor.gemini.scorer.SummarizeWrittenTextScorer;
import com.pte.scoring.internal.vendor.gemini.scorer.WriteEssayScorer;
import com.pte.scoring.internal.vendor.gemini.GeminiRestClient;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import com.pte.scoring.internal.vendor.stub.StubEssayScoringClient;
import com.pte.scoring.internal.vendor.stub.StubSpeechScoringClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AzureGeminiProviderConfigTest {

    private static final String[] COMPLETE_SETTINGS = {
            "scoring.ai.provider=azure-gemini",
            "scoring.ai.azure.speech-key=test-azure-key",
            "scoring.ai.azure.speech-region=southeastasia",
            "scoring.ai.gemini.api-key=test-gemini-key",
            "scoring.ai.gemini.model=gemini-flash-lite-latest"
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(MediaService.class, () -> mock(MediaService.class))
            .withUserConfiguration(AiProviderConfig.class, AzureGeminiProviderConfig.class,
                    StubSpeechScoringClient.class, StubEssayScoringClient.class, SpeechAudioLoader.class,
                    ReadAloudScorer.class, RepeatSentenceScorer.class, ShortQuestionScorer.class,
                    PromptImageLoader.class, RetellLectureScorer.class, DescribeImageScorer.class,
                    RespondToSituationScorer.class, SummarizeGroupDiscussionScorer.class,
                    SummarizeWrittenTextScorer.class, WriteEssayScorer.class, SummarizeSpokenTextScorer.class);

    @Test
    void defaultProviderKeepsTheStubAndCreatesNoVendorClientsOrScorers() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(StubSpeechScoringClient.class);
            assertThat(context).doesNotHaveBean(AzureSpeechRestClient.class);
            assertThat(context).doesNotHaveBean(GeminiRestClient.class);
            assertThat(context).doesNotHaveBean(AiAnswerScorer.class);
        });
    }

    @Test
    void completeSettingsCreateTheVendorClientsAndEveryAzureAndGeminiScorer() {
        runner.withPropertyValues(COMPLETE_SETTINGS).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AzureSpeechRestClient.class);
            assertThat(context).hasSingleBean(AzureSpeechAssessor.class);
            assertThat(context).hasSingleBean(GeminiRestClient.class);
            // The legacy stub stays as the fallback for the task types no scorer claims.
            assertThat(context).getBean(SpeechScoringClient.class).isInstanceOf(StubSpeechScoringClient.class);
            assertThat(context.getBeansOfType(AiAnswerScorer.class).values())
                    .flatExtracting(AiAnswerScorer::taskTypes)
                    .containsExactlyInAnyOrder(ScoringConstants.TASK_TYPE_READ_ALOUD,
                            ScoringConstants.TASK_TYPE_REPEAT_SENTENCE,
                            ScoringConstants.TASK_TYPE_ANSWER_SHORT_QUESTION,
                            ScoringConstants.TASK_TYPE_RE_TELL_LECTURE,
                            ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE,
                            ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION,
                            ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION,
                            ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT,
                            ScoringConstants.TASK_TYPE_WRITE_ESSAY,
                            ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT);
        });
    }

    @Test
    void missingSettingsFailStartupAndNameEveryOneOfThem() {
        runner.withPropertyValues("scoring.ai.provider=azure-gemini").run(context -> {
            assertThat(context).hasFailed();
            assertThat(failureMessages(context.getStartupFailure()))
                    .contains("SCORING_AI_AZURE_SPEECH_KEY")
                    .contains("SCORING_AI_AZURE_SPEECH_REGION")
                    .contains("SCORING_AI_GEMINI_API_KEY")
                    .doesNotContain("SCORING_AI_GEMINI_MODEL");
        });
    }

    @Test
    void regionThatIsNotAPlainAzureRegionNameFailsStartup() {
        runner.withPropertyValues(COMPLETE_SETTINGS)
                .withPropertyValues("scoring.ai.azure.speech-region=evil.example.com/x")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(failureMessages(context.getStartupFailure()))
                            .contains("SCORING_AI_AZURE_SPEECH_REGION")
                            .doesNotContain("SCORING_AI_AZURE_SPEECH_KEY");
                });
    }

    @Test
    void blankModelFailsStartup() {
        runner.withPropertyValues(COMPLETE_SETTINGS)
                .withPropertyValues("scoring.ai.gemini.model=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(failureMessages(context.getStartupFailure())).contains("SCORING_AI_GEMINI_MODEL");
                });
    }

    @Test
    void keyThatCannotBeSentAsAHeaderFailsStartupWithoutEchoingIt() {
        runner.withPropertyValues(COMPLETE_SETTINGS)
                .withPropertyValues("scoring.ai.gemini.api-key=pasted gemini key")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(failureMessages(context.getStartupFailure()))
                            .contains("SCORING_AI_GEMINI_API_KEY")
                            .doesNotContain("SCORING_AI_AZURE_SPEECH_KEY")
                            .doesNotContain("pasted gemini key");
                });
    }

    @Test
    void timeoutAboveSixtySecondsIsRejected() {
        runner.withPropertyValues(COMPLETE_SETTINGS)
                .withPropertyValues("scoring.ai.azure.timeout-ms=90000")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void unknownProviderIsRejectedWithTheSupportedValues() {
        runner.withPropertyValues("scoring.ai.provider=bogus").run(context -> {
            assertThat(context).hasFailed();
            assertThat(failureMessages(context.getStartupFailure())).contains("azure-gemini");
        });
    }

    private static String failureMessages(Throwable failure) {
        List<String> messages = new ArrayList<>();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            messages.add(String.valueOf(current.getMessage()));
        }
        return String.join("\n", messages);
    }
}
