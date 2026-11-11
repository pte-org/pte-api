package com.pte.scoring.internal.vendor.audioscoring;

import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.gemini.GeminiAudio;
import com.pte.scoring.internal.vendor.gemini.GeminiImage;
import com.pte.scoring.internal.vendor.gemini.GeminiRequest;
import com.pte.scoring.internal.vendor.gemini.GeminiResponse;
import com.pte.scoring.internal.vendor.gemini.PromptImageLoader;
import com.pte.scoring.internal.vendor.gemini.StructuredModelClient;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import com.pte.scoring.rubric.RubricScorer;
import com.pte.scoring.rubric.TraitScoreSet;
import com.pte.scoring.support.SpeakingRubrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Practice speaking heard by the scoring model: the recording goes to the model with the task's rubric, the model's
 * transcript feeds the traits code counts, the item score is RubricScorer's, and the feedback is kept for the student.
 */
class ModelAudioScorerTest {

    private static final String PASSAGE = "Solar power is becoming one of the most affordable sources of energy.";
    private static final int PASSAGE_WORDS = 12;
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID VERSION = UUID.randomUUID();
    private static final String MEDIA = UUID.randomUUID().toString();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SpeechAudioLoader audio = mock(SpeechAudioLoader.class);
    private final StructuredModelClient model = mock(StructuredModelClient.class);
    private final PromptImageLoader images = mock(PromptImageLoader.class);
    private ModelAudioScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new ModelAudioScorer(audio, model, images, JSON);
        when(audio.load(MEDIA, TENANT)).thenReturn(wav(1));
    }

    @Test
    void scoresTheSevenSpeakingTaskTypes() {
        assertThat(scorer.taskTypes()).containsExactlyInAnyOrder(ScoringConstants.TASK_TYPE_READ_ALOUD,
                ScoringConstants.TASK_TYPE_REPEAT_SENTENCE, ScoringConstants.TASK_TYPE_ANSWER_SHORT_QUESTION,
                ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE, ScoringConstants.TASK_TYPE_RE_TELL_LECTURE,
                ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION,
                ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION);
    }

    @Test
    void readAloudContentIsCountedFromTheTranscriptAndTheModelJudgesTheSound() {
        // "of the" omitted and "very" inserted: Content 12 - 3 = 9.
        givenModelAnswers("""
                {"transcript":"Solar power is becoming one most very affordable sources of energy",
                 "PRONUNCIATION":4,"ORAL_FLUENCY":3,"feedback":"Khá tốt.","tips":["Đọc theo cụm từ."],
                 "traitRationales":{"PRONUNCIATION":{"reason":"Rõ ràng.","evidence":"solar"},
                                    "ORAL_FLUENCY":{"reason":"Có ngập ngừng.","evidence":"most"}}}""");

        AiScoreResult result = scorer.score(request(ScoringConstants.TASK_TYPE_READ_ALOUD, PASSAGE, null, null));

        assertThat(result.subScores()).containsOnly(Map.entry("CONTENT", 9), Map.entry("PRONUNCIATION", 4),
                Map.entry("ORAL_FLUENCY", 3));
        assertThat(result.rawScore()).isEqualTo(RubricScorer.score(
                SpeakingRubrics.of(ScoringConstants.TASK_TYPE_READ_ALOUD),
                new TraitScoreSet(result.subScores(), PASSAGE_WORDS)).itemScore());
        assertThat(result.provider()).isEqualTo(ModelAudioScorer.PROVIDER);
        assertThat(result.feedback()).isEqualTo("Khá tốt.");
        assertThat(result.rubricVersionPublicId()).isEqualTo(VERSION);
        JsonNode detail = JSON.readTree(result.detailJson());
        assertThat(detail.path("tips").get(0).asText()).isEqualTo("Đọc theo cụm từ.");
        assertThat(detail.path("traitRationales").path("ORAL_FLUENCY").path("reason").asText())
                .isEqualTo("Có ngập ngừng.");
        assertThat(detail.path("wordMarks").toString())
                .contains("{\"word\":\"of\",\"mark\":\"OMITTED\"}", "{\"word\":\"very\",\"mark\":\"INSERTED\"}");
    }

    @Test
    void theRecordingAndTheRubricGoToTheModelButContentIsNotAskedForReadAloud() {
        givenModelAnswers("""
                {"transcript":"Solar power","PRONUNCIATION":2,"ORAL_FLUENCY":2,"feedback":"","tips":[],
                 "traitRationales":{}}""");

        scorer.score(request(ScoringConstants.TASK_TYPE_READ_ALOUD, PASSAGE, null, null));

        GeminiRequest sent = sentRequest();
        assertThat(sent.audio()).isNotNull();
        assertThat(sent.audio().format()).isEqualTo(GeminiAudio.WAV);
        assertThat(sent.userText()).contains(PASSAGE);
        assertThat(sent.systemInstruction()).contains("PRONUNCIATION", "ORAL_FLUENCY", "Vietnamese")
                .contains("is counted by code from your transcript");
        assertThat(sent.responseSchema().toString()).contains("transcript", "tips").doesNotContain("CONTENT=");
    }

    @Test
    void unscriptedTasksHaveEveryTraitJudgedByTheModel() {
        givenModelAnswers("""
                {"transcript":"The lecture said solar is cheap","CONTENT":3,"PRONUNCIATION":4,"ORAL_FLUENCY":4,
                 "feedback":"Tốt.","tips":["Nêu thêm ý chính."],"traitRationales":{}}""");

        AiScoreResult result = scorer.score(request(ScoringConstants.TASK_TYPE_RE_TELL_LECTURE, null,
                "Solar energy prices fell sharply over the last decade.", null));

        assertThat(result.subScores()).containsOnly(Map.entry("CONTENT", 3), Map.entry("PRONUNCIATION", 4),
                Map.entry("ORAL_FLUENCY", 4));
        assertThat(result.transcript()).isEqualTo("The lecture said solar is cheap");
    }

    @Test
    void describeImageSendsThePicture() {
        UUID picture = UUID.randomUUID();
        when(images.load(picture, TENANT)).thenReturn(new GeminiImage("image/png", new byte[]{1, 2}));
        givenModelAnswers("""
                {"transcript":"A bar chart","CONTENT":2,"PRONUNCIATION":3,"ORAL_FLUENCY":3,"feedback":"",
                 "tips":[],"traitRationales":{}}""");

        AiScoreResult result = scorer.score(new AiScoringRequest(ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE, MEDIA,
                null, null, null, picture, null, null, TENANT,
                SpeakingRubrics.of(ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE), VERSION));

        assertThat(sentRequest().images()).hasSize(1);
        assertThat(result.fallback()).isFalse();
    }

    @Test
    void anEmptyRecordingScoresZeroWithoutAModelCall() {
        when(audio.load(MEDIA, TENANT)).thenReturn(wav(0));

        AiScoreResult result = scorer.score(request(ScoringConstants.TASK_TYPE_READ_ALOUD, PASSAGE, null, null));

        assertThat(result.subScores()).containsOnly(Map.entry("CONTENT", 0), Map.entry("PRONUNCIATION", 0),
                Map.entry("ORAL_FLUENCY", 0));
        assertThat(result.rawScore()).isZero();
        verify(model, never()).generate(any());
    }

    @Test
    void noSpeechHeardScoresZeroWhateverTheModelGuessed() {
        givenModelAnswers("""
                {"transcript":"","PRONUNCIATION":3,"ORAL_FLUENCY":3,"feedback":"x","tips":[],"traitRationales":{}}""");

        AiScoreResult result = scorer.score(request(ScoringConstants.TASK_TYPE_REPEAT_SENTENCE, null,
                "The library opens at nine.", null));

        assertThat(result.subScores().values()).containsOnly(0);
        assertThat(result.feedback()).isEmpty();
    }

    @Test
    void answerShortQuestionScoresVocabularyFromTheAcceptedAnswers() {
        givenModelAnswers("""
                {"transcript":"Thermometer.","feedback":"Đúng.","tips":[],"traitRationales":{}}""");

        AiScoreResult result = scorer.score(request(ScoringConstants.TASK_TYPE_ANSWER_SHORT_QUESTION,
                "What measures temperature?", null, "thermometer"));

        assertThat(result.subScores()).containsOnly(Map.entry("VOCABULARY", 1));
    }

    @Test
    void anOutOfScaleTraitIsAskedAgainAndThenFails() {
        givenModelAnswers("""
                {"transcript":"Solar power","PRONUNCIATION":9,"ORAL_FLUENCY":2,"feedback":"","tips":[],
                 "traitRationales":{}}""");

        assertThatThrownBy(() -> scorer.score(request(ScoringConstants.TASK_TYPE_READ_ALOUD, PASSAGE, null, null)))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_RESPONSE_INVALID));
        verify(model, times(3)).generate(any());
    }

    @Test
    void aReadAloudWithoutAPassageFailsBeforeAnyAudioIsLoaded() {
        assertThatThrownBy(() -> scorer.score(request(ScoringConstants.TASK_TYPE_READ_ALOUD, " ", null, null)))
                .isInstanceOf(AiProviderException.class);
        verify(audio, never()).load(any(), any());
    }

    private void givenModelAnswers(String json) {
        when(model.generate(any())).thenReturn(new GeminiResponse(JSON.readTree(json), "Capstone"));
    }

    private GeminiRequest sentRequest() {
        ArgumentCaptor<GeminiRequest> captor = ArgumentCaptor.forClass(GeminiRequest.class);
        verify(model).generate(captor.capture());
        return captor.getValue();
    }

    private static AiScoringRequest request(String taskType, String promptText, String reference, String answerKey) {
        return new AiScoringRequest(taskType, MEDIA, promptText, reference, answerKey, null, null, null, TENANT,
                SpeakingRubrics.of(taskType), VERSION);
    }

    /** A 16 kHz mono 16-bit WAV of {@code seconds} seconds of a quiet tone. */
    private static byte[] wav(int seconds) {
        AudioFormat format = new AudioFormat(16_000, 16, 1, true, false);
        byte[] pcm = new byte[16_000 * 2 * seconds];
        for (int i = 0; i < pcm.length / 2; i++) {
            short sample = (short) (Math.sin(i / 8.0) * 3000);
            pcm[2 * i] = (byte) sample;
            pcm[2 * i + 1] = (byte) (sample >> 8);
        }
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(pcm), format, pcm.length / 2)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
