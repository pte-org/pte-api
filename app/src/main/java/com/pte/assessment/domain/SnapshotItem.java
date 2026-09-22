package com.pte.assessment.domain;

import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A frozen, self-contained copy of one question's content inside a snapshot.
 * Options are stored as a JSON string ({@code optionsJson}) so the item carries
 * everything needed to deliver + score the task with no reference back to the
 * mutable {@code itembank.Question}. Correct answers are retained here for the
 * scoring module; attempt strips them before serving to a student.
 */
@Entity
@Table(name = "snapshot_items", indexes = {
        @Index(name = "idx_snapshot_items_snapshot", columnList = "snapshot_id")
})
@Getter
@Setter
@NoArgsConstructor
public class SnapshotItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private ExamSnapshot snapshot;

    @Column(nullable = false)
    private UUID sourceQuestionPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PteTaskType pteTaskType;

    /** Canonical string code kept alongside the enum for the cross-module runtime contract. */
    @Column(name = "task_type_code", length = 64)
    private String taskTypeCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PteSection section;

    @Column(nullable = false)
    private int orderIndex;

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

    @Column(name = "runtime_mapping_version", length = 32)
    private String runtimeMappingVersion;

    @Column(name = "runtime_mapping_status", length = 32)
    private String runtimeMappingStatus;

    public void pinRuntimeProfile(TaskRuntimeProfileDescriptor profile, String mappingVersion,
            String mappingStatus) {
        this.taskTypeCode = profile.taskTypeCode();
        this.runtimeProfileKey = profile.profileKey();
        this.runtimeProfileVersion = profile.profileVersion();
        this.runtimeBehaviorKey = profile.behaviorKey();
        this.runtimeRendererKey = profile.rendererKey();
        this.runtimeAnswerSchemaVersion = profile.answerSchemaVersion();
        this.runtimeScoringProfileKey = profile.scoringProfileKey();
        this.runtimeScoringProfileVersion = profile.scoringProfileVersion();
        this.runtimeRequiredClientCapabilities = String.join(",", profile.requiredClientCapabilities());
        this.runtimeProfileStatus = profile.status();
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
                taskTypeCode, runtimeProfileKey,
                runtimeProfileVersion, runtimeBehaviorKey, runtimeRendererKey, runtimeAnswerSchemaVersion,
                runtimeScoringProfileKey, runtimeScoringProfileVersion, capabilities, runtimeProfileStatus);
    }

    /** A partially populated new contract is unsafe to reinterpret as legacy. */
    public boolean hasPartialRuntimeProfile() {
        boolean anyRuntimeField = runtimeProfileKey != null || runtimeProfileVersion != null
                || runtimeBehaviorKey != null || runtimeRendererKey != null
                || runtimeAnswerSchemaVersion != null || runtimeScoringProfileKey != null
                || runtimeScoringProfileVersion != null || runtimeRequiredClientCapabilities != null
                || runtimeProfileStatus != null;
        return anyRuntimeField && runtimeProfile() == null;
    }
}
