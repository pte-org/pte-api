package com.pte.examdelivery.service;

import com.pte.examdelivery.client.AuthoringClient;
import com.pte.examdelivery.client.MediaClient;
import com.pte.examdelivery.client.SchedulingClient;
import com.pte.examdelivery.client.dto.AuthoringSnapshotContentResponse;
import com.pte.examdelivery.client.dto.MediaPresignedDownloadResponse;
import com.pte.examdelivery.client.dto.SchedulingEntitlementResponse;
import com.pte.examdelivery.config.TaskTimingConfig;
import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.PinnedExamSnapshot;
import com.pte.examdelivery.domain.PinnedItem;
import com.pte.examdelivery.domain.exception.AudioResolutionFailedException;
import com.pte.examdelivery.domain.exception.MissingAudioDurationException;
import com.pte.examdelivery.domain.exception.MissingAudioPromptException;

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
 * Covers {@code SnapshotPinService.toPinnedItem}'s presign branch — widened
 * in plans/phat-speaking-audio-prompt-e2e to resolve non-LISTENING items'
 * {@code audioPromptRef} too (e.g. Speaking's REPEAT_SENTENCE), while
 * preserving LISTENING's existing required-audio invariant unchanged.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SnapshotPinService")
class SnapshotPinServiceTest {

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID SNAPSHOT_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID AUDIO_REF = UUID.randomUUID();

    @Mock
    private SchedulingClient schedulingClient;

    @Mock
    private AuthoringClient authoringClient;

    @Mock
    private MediaClient mediaClient;

    @Mock
    private TaskTimingConfig taskTimingConfig;

    private SnapshotPinService service;

    @BeforeEach
    void setUp() {
        // Real task-timing.json coverage is irrelevant to this presign-branch
        // test — stub a fixed Timing for whatever taskType is requested so
        // this test isn't coupled to (or blocked by) unrelated config gaps.
        // preListenSeconds/preRecordSeconds null here keeps every case in
        // this file on the static-prep branch (dynamic-prep-timing has its
        // own dedicated tests below, per plans/phat-speaking-dynamic-prep-timing).
        when(taskTimingConfig.timingFor(any())).thenReturn(new TaskTimingConfig.Timing(10, 10, null, null));
        service = new SnapshotPinService(schedulingClient, authoringClient, mediaClient, taskTimingConfig);
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
        when(mediaClient.presignGet(eq(AUDIO_REF), anyLong(), any())).thenReturn(presigned());

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
        when(mediaClient.presignGet(eq(AUDIO_REF), anyLong(), any())).thenReturn(presigned());

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
    @DisplayName("Presign failure on a non-LISTENING item with a non-null ref surfaces AudioResolutionFailedException")
    void speakingItem_presignFailure_surfacesAudioResolutionFailed() {
        stubEntitlement("REPEAT_SENTENCE");
        stubContent(item("SPEAKING", "REPEAT_SENTENCE", AUDIO_REF));
        when(mediaClient.presignGet(eq(AUDIO_REF), anyLong(), any())).thenReturn(null);

        assertThatThrownBy(() -> service.pin(attempt(), SESSION_ID, STUDENT_ID))
                .isInstanceOf(AudioResolutionFailedException.class);
    }

    // ------------------------------------------------------------------
    // Dynamic prep timing (plans/phat-speaking-dynamic-prep-timing) — a
    // per-test taskTimingConfig.timingFor(...) stub with non-null
    // preListenSeconds/preRecordSeconds overrides the class-level any()
    // stub above (Mockito matches the most specific/most-recent stub),
    // switching that one task type onto the dynamic branch.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("REPEAT_SENTENCE computes prepSeconds dynamically as preListen + real audio duration + preRecord")
    void repeatSentence_dynamicPrepTiming() {
        when(taskTimingConfig.timingFor("REPEAT_SENTENCE")).thenReturn(new TaskTimingConfig.Timing(10, 15, 3, 3));
        stubEntitlement("REPEAT_SENTENCE");
        stubContent(item("SPEAKING", "REPEAT_SENTENCE", AUDIO_REF));
        when(mediaClient.presignGet(eq(AUDIO_REF), anyLong(), any())).thenReturn(presignedWithDuration(6));

        PinnedExamSnapshot pinned = service.pin(attempt(), SESSION_ID, STUDENT_ID);

        PinnedItem pinnedItem = pinned.getItems().get(0);
        assertThat(pinnedItem.getPreListenSeconds()).isEqualTo(3);
        assertThat(pinnedItem.getPreRecordSeconds()).isEqualTo(3);
        // 3 (preListen) + 6 (real audio) + 3 (preRecord) = 12, not the static 10.
        assertThat(pinnedItem.getPrepSeconds()).isEqualTo(12);
        // Same call resolves both the URL and the duration — no second media call.
        verify(mediaClient, times(1)).presignGet(eq(AUDIO_REF), anyLong(), any());
    }

    @Test
    @DisplayName("RESPOND_TO_A_SITUATION preserves its existing combined 20s pre-listen value exactly")
    void respondToASituation_preservesPreListenValue() {
        when(taskTimingConfig.timingFor("RESPOND_TO_A_SITUATION")).thenReturn(new TaskTimingConfig.Timing(40, 40, 20, 10));
        stubEntitlement("RESPOND_TO_A_SITUATION");
        stubContent(item("SPEAKING", "RESPOND_TO_A_SITUATION", AUDIO_REF));
        when(mediaClient.presignGet(eq(AUDIO_REF), anyLong(), any())).thenReturn(presignedWithDuration(10));

        PinnedExamSnapshot pinned = service.pin(attempt(), SESSION_ID, STUDENT_ID);

        PinnedItem pinnedItem = pinned.getItems().get(0);
        assertThat(pinnedItem.getPreListenSeconds()).isEqualTo(20);
        assertThat(pinnedItem.getPreRecordSeconds()).isEqualTo(10);
        assertThat(pinnedItem.getPrepSeconds()).isEqualTo(40);
    }

    @Test
    @DisplayName("RE_TELL_LECTURE, ANSWER_SHORT_QUESTION, SUMMARIZE_GROUP_DISCUSSION all compute dynamic prepSeconds too")
    void remainingAudioPromptTypes_dynamicPrepTiming() {
        when(taskTimingConfig.timingFor("RE_TELL_LECTURE")).thenReturn(new TaskTimingConfig.Timing(70, 40, 3, 10));
        when(taskTimingConfig.timingFor("ANSWER_SHORT_QUESTION")).thenReturn(new TaskTimingConfig.Timing(14, 10, 3, 3));
        when(taskTimingConfig.timingFor("SUMMARIZE_GROUP_DISCUSSION")).thenReturn(new TaskTimingConfig.Timing(200, 120, 5, 10));
        when(mediaClient.presignGet(eq(AUDIO_REF), anyLong(), any())).thenReturn(presignedWithDuration(20));

        stubEntitlement("RE_TELL_LECTURE");
        stubContent(item("SPEAKING", "RE_TELL_LECTURE", AUDIO_REF));
        assertThat(service.pin(attempt(), SESSION_ID, STUDENT_ID).getItems().get(0).getPrepSeconds()).isEqualTo(3 + 20 + 10);

        stubEntitlement("ANSWER_SHORT_QUESTION");
        stubContent(item("SPEAKING", "ANSWER_SHORT_QUESTION", AUDIO_REF));
        assertThat(service.pin(attempt(), SESSION_ID, STUDENT_ID).getItems().get(0).getPrepSeconds()).isEqualTo(3 + 20 + 3);

        stubEntitlement("SUMMARIZE_GROUP_DISCUSSION");
        stubContent(item("SPEAKING", "SUMMARIZE_GROUP_DISCUSSION", AUDIO_REF));
        assertThat(service.pin(attempt(), SESSION_ID, STUDENT_ID).getItems().get(0).getPrepSeconds()).isEqualTo(5 + 20 + 10);
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
        when(mediaClient.presignGet(eq(AUDIO_REF), anyLong(), any())).thenReturn(presignedWithDuration(null));

        assertThatThrownBy(() -> service.pin(attempt(), SESSION_ID, STUDENT_ID))
                .isInstanceOf(MissingAudioDurationException.class);
    }

    private void stubEntitlement(String taskType) {
        SchedulingEntitlementResponse.Policy policy =
                new SchedulingEntitlementResponse.Policy("UNLIMITED", null, false, false, "STANDARD");
        SchedulingEntitlementResponse.CompositionItem composition =
                new SchedulingEntitlementResponse.CompositionItem(taskType, "SPEAKING", 0, null, null);
        SchedulingEntitlementResponse entitlement = new SchedulingEntitlementResponse(
                SESSION_ID, SNAPSHOT_ID, TENANT_ID, Instant.now(), Instant.now().plusSeconds(3600), policy,
                List.of(composition));
        when(schedulingClient.checkEntitlement(SESSION_ID, STUDENT_ID)).thenReturn(entitlement);
    }

    private void stubContent(AuthoringSnapshotContentResponse.Item item) {
        AuthoringSnapshotContentResponse content =
                new AuthoringSnapshotContentResponse(SNAPSHOT_ID, "snapshot", 1, TENANT_ID, List.of(item));
        when(authoringClient.fetchContent(SNAPSHOT_ID)).thenReturn(content);
    }

    private AuthoringSnapshotContentResponse.Item item(String section, String taskType, UUID audioPromptRef) {
        return new AuthoringSnapshotContentResponse.Item(0, section, taskType, "title", "prompt", audioPromptRef,
                null, null, null, null, null, null);
    }

    private MediaPresignedDownloadResponse presigned() {
        // durationSeconds (3rd arg) is null here — this test suite predates
        // plans/phat-speaking-dynamic-prep-timing and isn't exercising
        // duration-dependent behavior; that phase's own tests cover it.
        return new MediaPresignedDownloadResponse("https://minio.local/signed", 3600L, null);
    }

    private MediaPresignedDownloadResponse presignedWithDuration(Integer durationSeconds) {
        return new MediaPresignedDownloadResponse("https://minio.local/signed", 3600L, durationSeconds);
    }

    private ExamAttempt attempt() {
        return new ExamAttempt();
    }
}
