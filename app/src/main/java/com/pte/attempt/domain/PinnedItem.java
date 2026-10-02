package com.pte.attempt.domain;

import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One frozen, self-contained task in a pinned attempt. Carries the FULL content
 * (including {@code correctAnswerText}/{@code optionsJson} with correct flags)
 * because scoring needs it later — the student-facing mapper strips answer data
 * before returning a task to the client (see {@code AttemptMapper}).
 */
@Entity
@Table(name = "pinned_items", indexes = {
        @Index(name = "idx_pinned_items_snapshot", columnList = "pinned_snapshot_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PinnedItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pinned_snapshot_id", nullable = false)
    private PinnedExamSnapshot pinnedSnapshot;

    @Column(nullable = false)
    private int orderIndex;

    @Column(nullable = false)
    private String section;

    @Column(nullable = true)
    private String taskType;

    @Column(name = "task_type_key", nullable = false, length = 64)
    private String taskTypeKey;

    /** Additive canonical code; {@link #taskType} remains for old clients and historical rows. */
    @Column(name = "task_type_code", length = 64)
    private String taskTypeCode;

    @Column(name = "task_type_display_name", length = 128)
    private String taskTypeDisplayName;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String promptText;

    @Column
    private UUID audioPromptRef;

    @Column
    private UUID imagePromptRef;

    @Column(columnDefinition = "text")
    private String referenceAnswerText;

    @Column(columnDefinition = "text")
    private String correctAnswerText;

    @Column
    private Integer minWordCount;

    @Column
    private Integer maxWordCount;

    @Column(columnDefinition = "text")
    private String optionsJson;

    @Column(nullable = false)
    private int prepSeconds;

    @Column(nullable = false)
    private int responseSeconds;

    /** Null = inherit the pinned session-level replay policy; non-null always wins. */
    @Column
    private Integer maxPlayCountOverride;

    /** Resolved once from `media` at StartAttempt pin time; LISTENING-section items only. Never re-fetched. */
    @Column(columnDefinition = "text")
    private String audioUrl;

    @Column
    private Instant audioUrlExpiresAt;

    /**
     * Non-null only for the 5 audio-prompt Speaking task types — the
     * client-facing sub-stage split lengths, server-owned so a dynamic
     * {@link #prepSeconds} (preListenSeconds + real audio duration +
     * preRecordSeconds) is possible. Every other task type leaves both null.
     */
    @Column
    private Integer preListenSeconds;

    @Column
    private Integer preRecordSeconds;

    /**
     * Resolved once from `media` at StartAttempt pin time, exactly like
     * {@link #audioUrl} — but unlike audio (served via a separate on-demand
     * `/audio` endpoint that reads this entity directly), {@code imageUrl} is
     * threaded all the way into the student-facing {@code TaskView} response,
     * since a static image has no replay-limit/idempotency concern to gate
     * behind an endpoint. Non-null only when {@link #imagePromptRef} is
     * non-null (mandatory for DESCRIBE_IMAGE, optional/absent elsewhere).
     */
    @Column(columnDefinition = "text")
    private String imageUrl;

    @Column
    private Instant imageUrlExpiresAt;

    @Column(name = "runtime_profile_key", length = 96)
    private String runtimeProfileKey;

    @Column(name = "runtime_profile_version")
    private Integer runtimeProfileVersion;

    @Column(name = "runtime_behavior_key", length = 64)
    private String runtimeBehaviorKey;

    @Column(name = "runtime_renderer_key", length = 96)
    private String runtimeRendererKey;

    @Column(name = "runtime_answer_schema_version")
    private Integer runtimeAnswerSchemaVersion;

    @Column(name = "runtime_scoring_profile_key", length = 64)
    private String runtimeScoringProfileKey;

    @Column(name = "runtime_scoring_profile_version")
    private Integer runtimeScoringProfileVersion;

    @Column(name = "runtime_required_client_capabilities", length = 512)
    private String runtimeRequiredClientCapabilities;

    @Column(name = "runtime_profile_status", length = 16)
    private String runtimeProfileStatus;

    @Column(name = "runtime_screen_key", length = 96)
    private String runtimeScreenKey;

    @Column(name = "runtime_contract_version")
    private Integer runtimeContractVersion;

    @Column(name = "runtime_scoring_mode", length = 16)
    private String runtimeScoringMode;

    @Column(name = "runtime_min_supported_app_version", length = 32)
    private String runtimeMinSupportedAppVersion;

    @Column(name = "runtime_authoring_contract_key", length = 96)
    private String runtimeAuthoringContractKey;

    @Column(name = "runtime_authoring_contract_version")
    private Integer runtimeAuthoringContractVersion;

    @Column(name = "runtime_scoring_implementation_version", length = 96)
    private String runtimeScoringImplementationVersion;

    @Column(name = "runtime_scoring_configuration_digest", length = 128)
    private String runtimeScoringConfigurationDigest;

    @Column(name = "runtime_mapping_version", length = 32)
    private String runtimeMappingVersion;

    @Column(name = "runtime_mapping_status", length = 32)
    private String runtimeMappingStatus;

    public void pinRuntimeProfile(TaskRuntimeProfileDescriptor profile, String mappingVersion,
            String mappingStatus) {
        this.taskTypeCode = this.taskTypeKey == null ? profile.taskTypeCode() : this.taskTypeKey;
        this.runtimeProfileKey = profile.profileKey();
        this.runtimeProfileVersion = profile.profileVersion();
        this.runtimeBehaviorKey = profile.behaviorKey();
        this.runtimeRendererKey = profile.rendererKey();
        this.runtimeAnswerSchemaVersion = profile.answerSchemaVersion();
        this.runtimeScoringProfileKey = profile.scoringProfileKey();
        this.runtimeScoringProfileVersion = profile.scoringProfileVersion();
        this.runtimeRequiredClientCapabilities = String.join(",", profile.requiredClientCapabilities());
        this.runtimeProfileStatus = profile.status();
        this.runtimeScreenKey = profile.screenKey();
        this.runtimeContractVersion = profile.contractVersion();
        this.runtimeScoringMode = profile.scoringMode();
        this.runtimeMinSupportedAppVersion = profile.minSupportedAppVersion();
        this.runtimeAuthoringContractKey = profile.authoringContractKey();
        this.runtimeAuthoringContractVersion = profile.authoringContractVersion();
        this.runtimeScoringImplementationVersion = profile.scoringProfileKey() + ":v"
                + profile.scoringProfileVersion();
        this.runtimeScoringConfigurationDigest = configurationDigest(profile);
        this.runtimeMappingVersion = mappingVersion;
        this.runtimeMappingStatus = mappingStatus;
    }

    public TaskRuntimeProfileDescriptor runtimeProfile() {
        if (taskTypeCode == null || runtimeProfileKey == null || runtimeProfileVersion == null || runtimeBehaviorKey == null
                || runtimeRendererKey == null || runtimeAnswerSchemaVersion == null
                || runtimeScoringProfileKey == null || runtimeScoringProfileVersion == null
                || runtimeRequiredClientCapabilities == null || runtimeProfileStatus == null) {
            return null;
        }
        java.util.List<String> capabilities = runtimeRequiredClientCapabilities == null
                || runtimeRequiredClientCapabilities.isBlank()
                ? java.util.List.of()
                : java.util.Arrays.stream(runtimeRequiredClientCapabilities.split(",")).toList();
        return new TaskRuntimeProfileDescriptor(
                taskTypeCode, runtimeProfileKey, runtimeProfileVersion,
                runtimeBehaviorKey, runtimeRendererKey, runtimeAnswerSchemaVersion, runtimeScoringProfileKey,
                runtimeScoringProfileVersion, capabilities, runtimeProfileStatus,
                runtimeScreenKey == null ? runtimeRendererKey : runtimeScreenKey,
                runtimeContractVersion == null ? runtimeProfileVersion : runtimeContractVersion,
                runtimeScoringMode == null
                        ? ("UNSCORED".equals(runtimeScoringProfileKey) ? "NONE" : "SCORED")
                        : runtimeScoringMode,
                runtimeMinSupportedAppVersion,
                runtimeAuthoringContractKey == null ? "PTE." + taskTypeCode + "_AUTHORING"
                        : runtimeAuthoringContractKey,
                runtimeAuthoringContractVersion == null ? 1 : runtimeAuthoringContractVersion);
    }

    /** A partially populated new contract is unsafe to reinterpret as legacy. */
    public boolean hasPartialRuntimeProfile() {
        boolean anyRuntimeField = runtimeProfileKey != null || runtimeProfileVersion != null
                || runtimeBehaviorKey != null || runtimeRendererKey != null
                || runtimeAnswerSchemaVersion != null || runtimeScoringProfileKey != null
                || runtimeScoringProfileVersion != null || runtimeRequiredClientCapabilities != null
                || runtimeProfileStatus != null || runtimeScreenKey != null || runtimeContractVersion != null
                || runtimeScoringMode != null || runtimeScoringImplementationVersion != null
                || runtimeScoringConfigurationDigest != null;
        return anyRuntimeField && runtimeProfile() == null;
    }

    private String configurationDigest(TaskRuntimeProfileDescriptor profile) {
        String value = String.join("|", profile.screenKey(), Integer.toString(profile.contractVersion()),
                profile.behaviorKey(), Integer.toString(profile.answerSchemaVersion()), profile.scoringProfileKey(),
                Integer.toString(profile.scoringProfileVersion()), profile.scoringMode(),
                String.join(",", profile.requiredClientCapabilities()));
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(AttemptDomainConstants.PINNED_RUNTIME_PROFILE_SHA256_UNAVAILABLE, ex);
        }
    }
}
