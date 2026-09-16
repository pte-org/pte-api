package com.pte.attempt.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.PinnedExamSnapshot;
import com.pte.attempt.domain.PinnedItem;
import com.pte.attempt.internal.config.TaskTimingConfig;
import com.pte.attempt.internal.exception.MissingAudioDurationException;
import com.pte.attempt.internal.exception.MissingAudioPromptException;
import com.pte.attempt.internal.exception.MissingImagePromptException;
import com.pte.media.MediaService;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.session.SessionService;
import com.pte.session.dto.response.CompositionItemResponse;
import com.pte.session.dto.response.EntitlementResponse;
import com.pte.session.dto.response.ExamPolicyResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ported from services/exam-delivery's own SnapshotPinServiceTest (plans/modular-monolith
 * Phase 07 ★) — same coverage, with the three HTTP clients replaced by
 * in-process session/assessment/media calls. Covers {@code SnapshotPinService.toPinnedItem}'s
 * presign branch: resolves non-LISTENING items' {@code audioPromptRef} too
 * (e.g. Speaking's REPEAT_SENTENCE), while preserving LISTENING's required-audio
 * invariant. {@code AudioResolutionFailedException}/{@code ImageResolutionFailedException}
 * (503-shaped network-call artifacts) don't exist in this port — a presign
 * failure now propagates media's own exception unmodified (see
 * {@code presignFailure_propagatesMediaExceptionUnmodified}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SnapshotPinService")
class SnapshotPinServiceTest {

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID SNAPSHOT_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID AUDIO_REF = UUID.randomUUID();
    private static final UUID IMAGE_REF = UUID.randomUUID();

    @Mock
    private SessionService sessionService;

    @Mock
    private AssessmentService assessmentService;

    @Mock
    private MediaService mediaService;

    @Mock
    private TaskTimingConfig taskTimingConfig;

    private SnapshotPinService service;

    @BeforeEach
    void setUp() {
        // Real task-timing.json coverage is irrelevant to this presign-branch
        // test — stub a fixed Timing for whatever taskType is requested so
        // this test isn't coupled to (or blocked by) unrelated config gaps.
        when(taskTimingConfig.timingFor(any())).thenReturn(new TaskTimingConfig.Timing(10, 10, null, null));
        service = new SnapshotPinService(sessionService, assessmentService, mediaService, taskTimingConfig);
    }

    @Test
    @DisplayName("LISTENING item with null audioPromptRef still throws MissingAudioPromptException (regression)")
    void listeningItem_nullAudioPromptRef_throwsMissingAudioPrompt() {
        stubEntitlement("MC_LISTENING_SINGLE");
        stubContent(item("LISTENING", "MC_LISTENING_SINGLE", null));

        assertThatThrownBy(() -> service.pin(attempt(), SESSION_ID, STUDENT_ID))
                .isInstanceOf(MissingAudioPromptException.class);
    }

    @Test
    @DisplayName("LISTENING item with non-null audioPromptRef still presigns as before (regression)")
    void listeningItem_withAudioPromptRef_presignsAsBefore() {
        stubEntitlement("MC_LISTENING_SINGLE");
        stubContent(item("LISTENING", "MC_LISTENING_SINGLE", AUDIO_REF));
        when(mediaService.presignGet(eq(AUDIO_REF), anyLong(), any())).thenReturn(presigned());

        PinnedExamSnapshot pinned = service.pin(attempt(), SESSION_ID, STUDENT_ID);

        PinnedItem pinnedItem = pinned.getItems().get(0);
        assertThat(pinnedItem.getAudioUrl()).isEqualTo("https://minio.local/signed");
        assertThat(pinnedItem.getAudioUrlExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("Non-LISTENING item with non-null audioPromptRef presigns successfully")
    void speakingItem_withAudioPromptRef_presignsSuccessfully() {
        stubEntitlement("REPEAT_SENTENCE");
        stubContent(item("SPEAKING", "REPEAT_SENTENCE", AUDIO_REF));
        when(mediaService.presignGet(eq(AUDIO_REF), anyLong(), any())).thenReturn(presigned());

        PinnedExamSnapshot pinned = service.pin(attempt(), SESSION_ID, STUDENT_ID);

        PinnedItem pinnedItem = pinned.getItems().get(0);
        assertThat(pinnedItem.getAudioUrl()).isEqualTo("https://minio.local/signed");
        assertThat(pinnedItem.getAudioUrlExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("Non-LISTENING item with null audioPromptRef pins with no exception and no audioUrl")
    void speakingItem_nullAudioPromptRef_pinsSilently() {
        stubEntitlement("READ_ALOUD");
        stubContent(item("SPEAKING", "READ_ALOUD", null));

        PinnedExamSnapshot pinned = service.pin(attempt(), SESSION_ID, STUDENT_ID);

        PinnedItem pinnedItem = pinned.getItems().get(0);
        assertThat(pinnedItem.getAudioUrl()).isNull();
        assertThat(pinnedItem.getAudioUrlExpiresAt()).isNull();
    }

    @Test
    @DisplayName("Presign failure propagates media's own exception unmodified (in-process call, no defensive null-check wrapper)")
    void presignFailure_propagatesMediaExceptionUnmodified() {
        stubEntitlement("REPEAT_SENTENCE");
        stubContent(item("SPEAKING", "REPEAT_SENTENCE", AUDIO_REF));
        when(mediaService.presignGet(eq(AUDIO_REF), anyLong(), any()))
                .thenThrow(new com.pte.media.internal.exception.MediaNotFoundException());

        assertThatThrownBy(() -> service.pin(attempt(), SESSION_ID, STUDENT_ID))
                .isInstanceOf(com.pte.media.internal.exception.MediaNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // Image-URL resolution — mirrors the audio-resolution patterns above.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("DESCRIBE_IMAGE item with null imagePromptRef throws MissingImagePromptException")
    void describeImageItem_nullImagePromptRef_throwsMissingImagePrompt() {
        stubEntitlement("DESCRIBE_IMAGE");
        stubContent(item("SPEAKING", "DESCRIBE_IMAGE", null, null));

        assertThatThrownBy(() -> service.pin(attempt(), SESSION_ID, STUDENT_ID))
                .isInstanceOf(MissingImagePromptException.class);
    }

    @Test
    @DisplayName("Non-DESCRIBE_IMAGE item with null imagePromptRef pins with no exception and no imageUrl")
    void nonDescribeImageItem_nullImagePromptRef_pinsSilently() {
        stubEntitlement("READ_ALOUD");
        stubContent(item("SPEAKING", "READ_ALOUD", null, null));

        PinnedExamSnapshot pinned = service.pin(attempt(), SESSION_ID, STUDENT_ID);

        PinnedItem pinnedItem = pinned.getItems().get(0);
        assertThat(pinnedItem.getImageUrl()).isNull();
        assertThat(pinnedItem.getImageUrlExpiresAt()).isNull();
    }

    @Test
    @DisplayName("DESCRIBE_IMAGE item with non-null imagePromptRef presigns successfully")
    void describeImageItem_withImagePromptRef_presignsSuccessfully() {
        stubEntitlement("DESCRIBE_IMAGE");
        stubContent(item("SPEAKING", "DESCRIBE_IMAGE", null, IMAGE_REF));
        when(mediaService.presignGet(eq(IMAGE_REF), anyLong(), any())).thenReturn(presigned());

        PinnedExamSnapshot pinned = service.pin(attempt(), SESSION_ID, STUDENT_ID);

        PinnedItem pinnedItem = pinned.getItems().get(0);
        assertThat(pinnedItem.getImageUrl()).isEqualTo("https://minio.local/signed");
        assertThat(pinnedItem.getImageUrlExpiresAt()).isNotNull();
        assertThat(pinnedItem.getAudioUrl()).isNull();
    }

    // ------------------------------------------------------------------
    // Dynamic prep timing — a per-test taskTimingConfig.timingFor(...) stub
    // with non-null preListenSeconds/preRecordSeconds overrides the
    // class-level any() stub above, switching that one task type onto the
    // dynamic branch.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("REPEAT_SENTENCE computes prepSeconds dynamically as preListen + real audio duration + preRecord")
    void repeatSentence_dynamicPrepTiming() {
        when(taskTimingConfig.timingFor("REPEAT_SENTENCE")).thenReturn(new TaskTimingConfig.Timing(10, 15, 3, 3));
        stubEntitlement("REPEAT_SENTENCE");
        stubContent(item("SPEAKING", "REPEAT_SENTENCE", AUDIO_REF));
        when(mediaService.presignGet(eq(AUDIO_REF), anyLong(), any())).thenReturn(presignedWithDuration(6));

        PinnedExamSnapshot pinned = service.pin(attempt(), SESSION_ID, STUDENT_ID);

        PinnedItem pinnedItem = pinned.getItems().get(0);
        assertThat(pinnedItem.getPreListenSeconds()).isEqualTo(3);
        assertThat(pinnedItem.getPreRecordSeconds()).isEqualTo(3);
        // 3 (preListen) + 6 (real audio) + 3 (preRecord) = 12, not the static 10.
        assertThat(pinnedItem.getPrepSeconds()).isEqualTo(12);
        // Same call resolves both the URL and the duration — no second media call.
        verify(mediaService, times(1)).presignGet(eq(AUDIO_REF), anyLong(), any());
    }

    @Test
    @DisplayName("RESPOND_TO_A_SITUATION preserves its existing combined 20s pre-listen value exactly")
    void respondToASituation_preservesPreListenValue() {
        when(taskTimingConfig.timingFor("RESPOND_TO_A_SITUATION")).thenReturn(new TaskTimingConfig.Timing(40, 40, 20, 10));
        stubEntitlement("RESPOND_TO_A_SITUATION");
        stubContent(item("SPEAKING", "RESPOND_TO_A_SITUATION", AUDIO_REF));
        when(mediaService.presignGet(eq(AUDIO_REF), anyLong(), any())).thenReturn(presignedWithDuration(10));

        PinnedExamSnapshot pinned = service.pin(attempt(), SESSION_ID, STUDENT_ID);

        PinnedItem pinnedItem = pinned.getItems().get(0);
        assertThat(pinnedItem.getPreListenSeconds()).isEqualTo(20);
        assertThat(pinnedItem.getPreRecordSeconds()).isEqualTo(10);
        assertThat(pinnedItem.getPrepSeconds()).isEqualTo(40);
    }

    @Test
    @DisplayName("Read Aloud (non-audio-prompt type) keeps its static prepSeconds and never populates preListen/preRecord")
    void readAloud_staticPrepTiming_noRegression() {
        when(taskTimingConfig.timingFor("READ_ALOUD")).thenReturn(new TaskTimingConfig.Timing(35, 40, null, null));
        stubEntitlement("READ_ALOUD");
        stubContent(item("SPEAKING", "READ_ALOUD", null));

        PinnedExamSnapshot pinned = service.pin(attempt(), SESSION_ID, STUDENT_ID);

        PinnedItem pinnedItem = pinned.getItems().get(0);
        assertThat(pinnedItem.getPrepSeconds()).isEqualTo(35);
        assertThat(pinnedItem.getPreListenSeconds()).isNull();
        assertThat(pinnedItem.getPreRecordSeconds()).isNull();
    }

    @Test
    @DisplayName("An audio-prompt type whose resolved media response carries no duration fails with MissingAudioDurationException")
    void audioPromptType_missingDuration_throws() {
        when(taskTimingConfig.timingFor("ANSWER_SHORT_QUESTION")).thenReturn(new TaskTimingConfig.Timing(14, 10, 3, 3));
        stubEntitlement("ANSWER_SHORT_QUESTION");
        stubContent(item("SPEAKING", "ANSWER_SHORT_QUESTION", AUDIO_REF));
        when(mediaService.presignGet(eq(AUDIO_REF), anyLong(), any())).thenReturn(presignedWithDuration(null));

        assertThatThrownBy(() -> service.pin(attempt(), SESSION_ID, STUDENT_ID))
                .isInstanceOf(MissingAudioDurationException.class);
    }

    private void stubEntitlement(String taskType) {
        ExamPolicyResponse policy = new ExamPolicyResponse("UNLIMITED", null, false, false, "STANDARD", "NONE");
        CompositionItemResponse composition = new CompositionItemResponse(taskType, "SPEAKING", 0, null, null);
        EntitlementResponse entitlement = new EntitlementResponse(
                SESSION_ID, SNAPSHOT_ID, TENANT_ID, Instant.now(), Instant.now().plusSeconds(3600), policy,
                List.of(composition));
        when(sessionService.checkEntitlement(SESSION_ID, STUDENT_ID)).thenReturn(entitlement);
    }

    private void stubContent(SnapshotContentResponse.Item item) {
        SnapshotContentResponse content =
                new SnapshotContentResponse(SNAPSHOT_ID, "snapshot", 1, TENANT_ID, List.of(item));
        when(assessmentService.getFullContent(SNAPSHOT_ID)).thenReturn(content);
    }

    private SnapshotContentResponse.Item item(String section, String taskType, UUID audioPromptRef) {
        return item(section, taskType, audioPromptRef, null);
    }

    private SnapshotContentResponse.Item item(String section, String taskType, UUID audioPromptRef,
                                                        UUID imagePromptRef) {
        return new SnapshotContentResponse.Item(0, section, taskType, "title", "prompt", audioPromptRef,
                imagePromptRef, null, null, null, null, null);
    }

    private PresignedDownloadResponse presigned() {
        return new PresignedDownloadResponse("https://minio.local/signed", 3600L, null);
    }

    private PresignedDownloadResponse presignedWithDuration(Integer durationSeconds) {
        return new PresignedDownloadResponse("https://minio.local/signed", 3600L, durationSeconds);
    }

    private ExamAttempt attempt() {
        return new ExamAttempt();
    }
}
