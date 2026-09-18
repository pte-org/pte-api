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
import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.session.SessionService;
import com.pte.session.dto.response.EntitlementResponse;
import com.pte.session.dto.response.ExamPolicyResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
 *
 * <p>Since Phase 3 (plans/score-template-exam-generation): every test below
 * that doesn't care about template-sourced timing runs against a stubbed
 * ACTIVE template with NO items — {@code toPinnedItem} then falls back to
 * {@code taskTimingConfig} exactly as before Phase 3, so none of these
 * regression tests needed to change. The template-priority behavior itself
 * gets its own dedicated tests further down.
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
    private static final UUID SCORE_TEMPLATE_ID = UUID.randomUUID();

    @Mock
    private SessionService sessionService;

    @Mock
    private AssessmentService assessmentService;

    @Mock
    private MediaService mediaService;

    @Mock
    private TaskTimingConfig taskTimingConfig;

    @Mock
    private ScoreTemplateService scoreTemplateService;

    private SnapshotPinService service;

    @BeforeEach
    void setUp() {
        // Real task-timing.json coverage is irrelevant to this presign-branch
        // test — stub a fixed Timing for whatever taskType is requested so
        // this test isn't coupled to (or blocked by) unrelated config gaps.
        // lenient(): only used by tests whose taskType is absent from the
        // stubbed template (the fallback path) — a template-known taskType
        // never reaches timingFor(any()) at all, which would otherwise trip
        // strict-stubs' UnnecessaryStubbingException on those tests.
        lenient().when(taskTimingConfig.timingFor(any())).thenReturn(new TaskTimingConfig.Timing(10, 10, null, null));
        // Default: the pinned template has no rows for any taskType used below,
        // so toPinnedItem always falls back to taskTimingConfig — the exact
        // pre-Phase-3 behavior these regression tests were written against.
        stubTemplateItems();
        service = new SnapshotPinService(sessionService, assessmentService, mediaService, taskTimingConfig,
                scoreTemplateService);
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
        assertThat(pinnedItem.getAudioUrl()).isEqualTo("https://res.cloudinary.com/test/signed");
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
        assertThat(pinnedItem.getAudioUrl()).isEqualTo("https://res.cloudinary.com/test/signed");
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
        assertThat(pinnedItem.getImageUrl()).isEqualTo("https://res.cloudinary.com/test/signed");
        assertThat(pinnedItem.getImageUrlExpiresAt()).isNotNull();
        assertThat(pinnedItem.getAudioUrl()).isNull();
    }

    // ------------------------------------------------------------------
    // Dynamic prep timing (template has no row -> taskTimingConfig fallback,
    // same as pre-Phase-3) — a per-test taskTimingConfig.timingFor(...) stub
    // with non-null preListenSeconds/preRecordSeconds overrides the
    // class-level any() stub above, switching that one task type onto the
    // dynamic branch.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("REPEAT_SENTENCE computes prepSeconds dynamically as preListen + real audio duration + preRecord")
    void repeatSentence_dynamicPrepTiming() {
        // Audio-prompt types always have a template row in real usage (FR-05
        // guarantees it) — templateItem's prepSeconds column IS preRecordSeconds
        // here (3, matching this test's expected formula), taskTimingConfig only
        // still supplies preListenSeconds.
        when(taskTimingConfig.timingForIfConfigured("REPEAT_SENTENCE")).thenReturn(new TaskTimingConfig.Timing(0, 0, 3, null));
        stubTemplateItems(templateItem("REPEAT_SENTENCE", "SPEAKING", 3, 15));
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
        when(taskTimingConfig.timingForIfConfigured("RESPOND_TO_A_SITUATION")).thenReturn(new TaskTimingConfig.Timing(0, 0, 20, null));
        stubTemplateItems(templateItem("RESPOND_TO_A_SITUATION", "SPEAKING", 10, 40));
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

    // ------------------------------------------------------------------
    // Template-priority behavior (Phase 3, spec FR-14) — the pinned
    // ScoreTemplate wins over taskTimingConfig whenever it has a row for
    // that taskType.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A static template-known type (e.g. MC_READING_SINGLE) uses the template's prep/responseSeconds, not taskTimingConfig's")
    void templateKnownStaticType_usesTemplateValues_notTaskTimingConfigFallback() {
        stubEntitlement("MC_READING_SINGLE");
        stubContent(item("READING", "MC_READING_SINGLE", null));
        // taskTimingConfig's class-level any() stub returns Timing(10, 10, ...) —
        // if production wrongly used it, these assertions below would fail.
        stubTemplateItems(templateItem("MC_READING_SINGLE", "READING", 0, 77));

        PinnedExamSnapshot pinned = service.pin(attempt(), SESSION_ID, STUDENT_ID);

        PinnedItem pinnedItem = pinned.getItems().get(0);
        assertThat(pinnedItem.getResponseSeconds()).isEqualTo(77);
        assertThat(pinnedItem.getPrepSeconds()).isEqualTo(0);
        assertThat(pinnedItem.getPreListenSeconds()).isNull();
        assertThat(pinnedItem.getPreRecordSeconds()).isNull();
    }

    @Test
    @DisplayName("An audio-prompt type's preRecordSeconds/prepSeconds come from the template's prep column, preListenSeconds still from taskTimingConfig")
    void audioPromptType_preRecordFromTemplate_preListenFromJson() {
        // Matches the slimmed task-timing.json shape since Phase 3: only preListenSeconds remains.
        when(taskTimingConfig.timingForIfConfigured("REPEAT_SENTENCE")).thenReturn(new TaskTimingConfig.Timing(0, 0, 3, null));
        stubEntitlement("REPEAT_SENTENCE");
        stubContent(item("SPEAKING", "REPEAT_SENTENCE", AUDIO_REF));
        stubTemplateItems(templateItem("REPEAT_SENTENCE", "SPEAKING", 3, 15));
        when(mediaService.presignGet(eq(AUDIO_REF), anyLong(), any())).thenReturn(presignedWithDuration(6));

        PinnedExamSnapshot pinned = service.pin(attempt(), SESSION_ID, STUDENT_ID);

        PinnedItem pinnedItem = pinned.getItems().get(0);
        assertThat(pinnedItem.getResponseSeconds()).isEqualTo(15); // from template, not taskTimingConfig's 0
        assertThat(pinnedItem.getPreListenSeconds()).isEqualTo(3); // still from taskTimingConfig
        assertThat(pinnedItem.getPreRecordSeconds()).isEqualTo(3); // from template's prep column, not taskTimingConfig
        assertThat(pinnedItem.getPrepSeconds()).isEqualTo(12); // 3 (preListen) + 6 (real audio) + 3 (preRecord, from template)
    }

    @Test
    @DisplayName("PERSONAL_INTRODUCTION (not in any ScoreTemplate) still falls back to taskTimingConfig and pins without error")
    void toPinnedItem_taskTypeNotInTemplate_fallsBackToTaskTimingConfig() {
        when(taskTimingConfig.timingFor("PERSONAL_INTRODUCTION")).thenReturn(new TaskTimingConfig.Timing(25, 30, null, null));
        stubEntitlement("PERSONAL_INTRODUCTION");
        stubContent(item("SPEAKING", "PERSONAL_INTRODUCTION", null));
        // Template has real rows for other task types, but none for PERSONAL_INTRODUCTION.
        stubTemplateItems(templateItem("READ_ALOUD", "SPEAKING", 35, 40));

        PinnedExamSnapshot pinned = service.pin(attempt(), SESSION_ID, STUDENT_ID);

        PinnedItem pinnedItem = pinned.getItems().get(0);
        assertThat(pinnedItem.getPrepSeconds()).isEqualTo(25);
        assertThat(pinnedItem.getResponseSeconds()).isEqualTo(30);
    }

    @Test
    @DisplayName("PinnedExamSnapshot.scoreTemplatePublicId is copied once from the source snapshot's pinned template")
    void pin_copiesScoreTemplatePublicIdFromContent() {
        stubEntitlement("READ_ALOUD");
        stubContent(item("SPEAKING", "READ_ALOUD", null));

        PinnedExamSnapshot pinned = service.pin(attempt(), SESSION_ID, STUDENT_ID);

        assertThat(pinned.getScoreTemplatePublicId()).isEqualTo(SCORE_TEMPLATE_ID);
    }

    /** {@code taskType} is unused now (Plan B: no composition to filter by) — kept as a param so every existing call site reads unchanged. */
    // ------------------------------------------------------------------
    // Plan B, Phase 3: SessionComposition removed — pin ALL snapshot items,
    // no per-type override.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Every item of the snapshot is pinned, in original orderIndex order — no composition filter")
    void pin_pinsAllSnapshotItemsInOriginalOrder_noCompositionFilter() {
        stubEntitlement("READ_ALOUD");
        SnapshotContentResponse.Item first = new SnapshotContentResponse.Item(
                0, "SPEAKING", "READ_ALOUD", "t1", "p1", null, null, null, null, null, null, null);
        SnapshotContentResponse.Item second = new SnapshotContentResponse.Item(
                1, "READING", "MC_READING_SINGLE", "t2", "p2", null, null, null, null, null, null, null);
        SnapshotContentResponse content = new SnapshotContentResponse(
                SNAPSHOT_ID, "snapshot", 1, SCORE_TEMPLATE_ID, TENANT_ID, List.of(second, first));
        when(assessmentService.getFullContent(SNAPSHOT_ID)).thenReturn(content);

        PinnedExamSnapshot pinned = service.pin(attempt(), SESSION_ID, STUDENT_ID);

        assertThat(pinned.getItems()).hasSize(2);
        assertThat(pinned.getItems().get(0).getTaskType()).isEqualTo("READ_ALOUD");
        assertThat(pinned.getItems().get(1).getTaskType()).isEqualTo("MC_READING_SINGLE");
    }

    @Test
    @DisplayName("responseSeconds always comes from the template/json value — there is no override to apply anymore")
    void pin_responseSecondsAlwaysFromTemplateOrJson_neverOverridden() {
        stubEntitlement("MC_READING_SINGLE");
        stubContent(item("READING", "MC_READING_SINGLE", null));
        stubTemplateItems(templateItem("MC_READING_SINGLE", "READING", 0, 77));

        PinnedExamSnapshot pinned = service.pin(attempt(), SESSION_ID, STUDENT_ID);

        assertThat(pinned.getItems().get(0).getResponseSeconds()).isEqualTo(77);
    }

    @Test
    @DisplayName("maxPlayCountOverride is always null — no composition to source it from")
    void pin_maxPlayCountOverrideAlwaysNull() {
        stubEntitlement("READ_ALOUD");
        stubContent(item("SPEAKING", "READ_ALOUD", null));

        PinnedExamSnapshot pinned = service.pin(attempt(), SESSION_ID, STUDENT_ID);

        assertThat(pinned.getItems().get(0).getMaxPlayCountOverride()).isNull();
    }

    private void stubEntitlement(String taskType) {
        ExamPolicyResponse policy = new ExamPolicyResponse("UNLIMITED", null, false, false, "STANDARD", "NONE");
        EntitlementResponse entitlement = new EntitlementResponse(
                SESSION_ID, SNAPSHOT_ID, TENANT_ID, Instant.now(), Instant.now().plusSeconds(3600), policy);
        when(sessionService.checkEntitlement(SESSION_ID, STUDENT_ID)).thenReturn(entitlement);
    }

    private void stubContent(SnapshotContentResponse.Item item) {
        SnapshotContentResponse content =
                new SnapshotContentResponse(SNAPSHOT_ID, "snapshot", 1, SCORE_TEMPLATE_ID, TENANT_ID, List.of(item));
        when(assessmentService.getFullContent(SNAPSHOT_ID)).thenReturn(content);
    }

    /** Stubs the ACTIVE-at-pin-time template's items — empty by default (see setUp), override per test to exercise template-priority behavior. */
    private void stubTemplateItems(ScoreTemplateItemResponse... items) {
        when(scoreTemplateService.getByPublicId(SCORE_TEMPLATE_ID))
                .thenReturn(new ScoreTemplateResponse(SCORE_TEMPLATE_ID, "APEUNI_V5", 1, "APEUni V5", "ACTIVE", List.of(items)));
    }

    private ScoreTemplateItemResponse templateItem(String taskType, String section, int prepSeconds, int responseSeconds) {
        return new ScoreTemplateItemResponse(taskType, section, 0, 1, 1, prepSeconds, responseSeconds, "FIXED",
                "OBJECTIVE", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
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
        return new PresignedDownloadResponse("https://res.cloudinary.com/test/signed", 3600L, null);
    }

    private PresignedDownloadResponse presignedWithDuration(Integer durationSeconds) {
        return new PresignedDownloadResponse("https://res.cloudinary.com/test/signed", 3600L, durationSeconds);
    }

    private ExamAttempt attempt() {
        return new ExamAttempt();
    }
}
