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
        when(taskTimingConfig.timingFor(any())).thenReturn(new TaskTimingConfig.Timing(10, 10));
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
        return new MediaPresignedDownloadResponse("https://minio.local/signed", 3600L);
    }

    private ExamAttempt attempt() {
        return new ExamAttempt();
    }
}
