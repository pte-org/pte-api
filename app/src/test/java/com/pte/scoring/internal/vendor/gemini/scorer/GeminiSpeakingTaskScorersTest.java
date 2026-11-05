package com.pte.scoring.internal.vendor.gemini.scorer;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.azure.AzureSpeechAssessor;
import com.pte.scoring.internal.vendor.azure.SpeechAssessment;
import com.pte.scoring.internal.vendor.azure.SpeechSegmentResult;
import com.pte.scoring.internal.vendor.gemini.GeminiRequest;
import com.pte.scoring.internal.vendor.gemini.GeminiResponse;
import com.pte.scoring.internal.vendor.gemini.GeminiRestClient;
import com.pte.scoring.internal.vendor.gemini.PromptImageLoader;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import com.pte.scoring.support.SpeakingRubrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

import static com.pte.scoring.internal.vendor.gemini.scorer.GeminiSpeakingScorersTest.CHUNKS;
import static com.pte.scoring.internal.vendor.gemini.scorer.GeminiSpeakingScorersTest.JSON;
import static com.pte.scoring.internal.vendor.gemini.scorer.GeminiSpeakingScorersTest.MEDIA;
import static com.pte.scoring.internal.vendor.gemini.scorer.GeminiSpeakingScorersTest.MODEL_VERSION;
import static com.pte.scoring.internal.vendor.gemini.scorer.GeminiSpeakingScorersTest.TENANT;
import static com.pte.scoring.internal.vendor.gemini.scorer.GeminiSpeakingScorersTest.VERSION;
import static com.pte.scoring.internal.vendor.gemini.scorer.GeminiSpeakingScorersTest.detail;
import static com.pte.scoring.internal.vendor.gemini.scorer.GeminiSpeakingScorersTest.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** What each unscripted speaking task asks Gemini for and keeps as detail, and when it refuses an item. */
@ExtendWith(MockitoExtension.class)
class GeminiSpeakingTaskScorersTest {

    private static final byte[] AUDIO = {1, 2, 3};

    @Mock
    private SpeechAudioLoader audio;
    @Mock
    private AzureSpeechAssessor assessor;
    @Mock
    private GeminiRestClient gemini;
    @Mock
    private PromptImageLoader images;
    private DescribeImageScorer describeImage;
    private RespondToSituationScorer situation;
    private SummarizeGroupDiscussionScorer discussion;
    private RetellLectureScorer retell;

    @BeforeEach
    void setUp() {
        describeImage = new DescribeImageScorer(audio, assessor, gemini, JSON, images);
        situation = new RespondToSituationScorer(audio, assessor, gemini, JSON);
        discussion = new SummarizeGroupDiscussionScorer(audio, assessor, gemini, JSON);
        retell = new RetellLectureScorer(audio, assessor, gemini, JSON);
        lenient().when(audio.load(MEDIA, TENANT)).thenReturn(AUDIO);
        lenient().when(assessor.prepare(AUDIO)).thenReturn(CHUNKS);
    }

    @Test
    void eachScorerHandlesItsOwnTaskTypeOnly() {
        assertThat(retell.taskTypes()).containsExactly(ScoringConstants.TASK_TYPE_RE_TELL_LECTURE);
        assertThat(describeImage.taskTypes()).containsExactly(ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE);
        assertThat(situation.taskTypes()).containsExactly(ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION);
        assertThat(discussion.taskTypes()).containsExactly(ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION);
    }

    @Test
    void describeImageFallsBackToTheReferenceDescriptionAndMarksTheScoreLowConfidence() {
        UUID imageRef = UUID.randomUUID();
        when(images.load(imageRef, TENANT)).thenThrow(new AiProviderException(
                ScoringConstants.PROMPT_IMAGE_DOWNLOAD_FAILED));
        heard("A bar chart of sales.");
        geminiSays("{\"CONTENT\": 4, \"feedback\": \"Ok.\", \"missed_details\": []}");

        AiScoreResult result = describeImage.score(new AiScoringRequest(ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE,
                MEDIA, null, "A chart of sales", null, imageRef, null, null, TENANT,
                SpeakingRubrics.of(ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE), VERSION));

        assertThat(result.fallback()).isTrue();
        JsonNode detail = detail(result);
        assertThat(detail.path("descriptionSource").asText()).isEqualTo("REFERENCE_TEXT");
        assertThat(detail.path("fallbackReason").asText()).isEqualTo(ScoringConstants.PROMPT_IMAGE_DOWNLOAD_FAILED);
        assertThat(detail.path("confidence").asText()).isEqualTo("LOW");
        GeminiRequest sent = sentRequest();
        assertThat(sent.images()).isEmpty();
        assertThat(sent.systemInstruction()).contains("reference description");
        assertThat(sent.userText()).contains("\"\"\"\nA chart of sales\n\"\"\"");
    }

    @Test
    void describeImageWithoutAnImageReferenceFallsBackWithoutTryingToLoad() {
        heard("A bar chart of sales.");
        geminiSays("{\"CONTENT\": 4, \"feedback\": \"\", \"missed_details\": []}");

        AiScoreResult result = describeImage.score(request(ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE, "A chart"));

        assertThat(result.fallback()).isTrue();
        assertThat(detail(result).path("fallbackReason").asText()).isEqualTo("NO_IMAGE_REFERENCE");
        verifyNoInteractions(images);
    }

    @Test
    void describeImageWithNeitherImageNorReferenceFailsBeforeAnyAudioIsDownloaded() {
        assertThatThrownBy(() -> describeImage.score(request(ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE, " ")))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> assertThat(ex.getMessage())
                        .isEqualTo(ScoringConstants.DESCRIBE_IMAGE_SOURCE_MISSING));
        verifyNoInteractions(audio, assessor, gemini);
    }

    @Test
    void situationKeepsTheRequirementCheckAsDetailAndScoresOnlyContent() {
        heard("Dear Professor, I would like to ask for an extension.");
        geminiSays("{\"CONTENT\": 5, \"feedback\": \"Polite.\", \"expected_register\": \"formal\","
                + " \"required_facts\": [{\"requirement\": \"ask extension\", \"status\": \"addressed\","
                + " \"evidence\": \"ask for an extension\"}], \"missed_requirements\": [],"
                + " \"misinterpreted_requirements\": []}");

        AiScoreResult result = situation.score(request(ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION,
                "You missed a deadline."));

        assertThat(result.subScores()).containsOnlyKeys("CONTENT", "PRONUNCIATION", "ORAL_FLUENCY");
        JsonNode detail = detail(result);
        assertThat(detail.path("expected_register").asText()).isEqualTo("formal");
        assertThat(detail.path("required_facts").path(0).path("status").asText()).isEqualTo("addressed");
        GeminiRequest sent = sentRequest();
        assertThat(sent.userText()).contains("\"\"\"\nYou missed a deadline.\n\"\"\"");
        assertThat(sent.responseSchema().get("required")).isEqualTo(List.of("CONTENT", "feedback",
                "expected_register", "required_facts", "missed_requirements", "misinterpreted_requirements"));
    }

    @Test
    void situationWithoutAPromptFails() {
        assertThatThrownBy(() -> situation.score(new AiScoringRequest(
                ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION, MEDIA, " ", null, null, null, null, null, TENANT,
                SpeakingRubrics.of(ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION), VERSION)))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ScoringConstants.AI_SOURCE_TEXT_EMPTY));
        verifyNoInteractions(audio);
    }

    @Test
    void discussionKeepsTheSpeakerBreakdownAsDetail() {
        String source = "[Speaker A] Remote work saves time. [Speaker B] But it isolates people.";
        heard("One speaker praises remote work and the other warns of isolation.");
        geminiSays("{\"CONTENT\": 5, \"feedback\": \"Good.\", \"overall_topic\": \"remote work\","
                + " \"speaker_contributions\": [{\"speaker\": \"A\", \"contribution_summary\": \"saves time\","
                + " \"covered_points\": [\"time\"], \"missed_points\": [], \"relationships\": []}],"
                + " \"covered_points\": [\"time\"], \"missed_points\": [\"isolation detail\"],"
                + " \"relationships\": [\"contrast\"]}");

        AiScoreResult result = discussion.score(request(ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION,
                source));

        assertThat(result.subScores()).containsEntry("CONTENT", 5);
        JsonNode detail = detail(result);
        assertThat(detail.path("overall_topic").asText()).isEqualTo("remote work");
        assertThat(detail.path("speaker_contributions").path(0).path("speaker").asText()).isEqualTo("A");
        assertThat(detail.path("missed_points").path(0).asText()).isEqualTo("isolation detail");
        assertThat(sentRequest().userText()).contains(source);
    }

    @Test
    void discussionAndLectureWithoutASourceTranscriptFail() {
        assertThatThrownBy(() -> discussion.score(request(ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION,
                null))).isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ScoringConstants.AI_SOURCE_TEXT_EMPTY));
        assertThatThrownBy(() -> retell.score(request(ScoringConstants.TASK_TYPE_RE_TELL_LECTURE, " ")))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ScoringConstants.AI_SOURCE_TEXT_EMPTY));
        verifyNoInteractions(audio);
    }

    private void heard(String transcript) {
        SpeechSegmentResult segment = new SpeechSegmentResult(true, transcript, transcript.toLowerCase(), 0,
                50_000_000L, null, List.of());
        when(assessor.assess(CHUNKS, "")).thenReturn(
                new SpeechAssessment(80.0, 70.0, 70.0, List.of(), List.of(segment), 5_000));
    }

    private void geminiSays(String json) {
        when(gemini.generate(any())).thenReturn(new GeminiResponse(JSON.readTree(json), MODEL_VERSION));
    }

    private GeminiRequest sentRequest() {
        ArgumentCaptor<GeminiRequest> captor = ArgumentCaptor.forClass(GeminiRequest.class);
        verify(gemini).generate(captor.capture());
        return captor.getValue();
    }
}
