package com.pte.scoretemplate.domain;

import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.scoretemplate.domain.enums.ScoringMethod;
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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * One task-type row of a {@link ScoreTemplate} (one of the 22 scored PTE
 * task types). {@code taskType}/{@code section} are plain {@code String}
 * (not {@code itembank.PteTaskType}/{@code PteSection}) on purpose — this
 * module stays independent of {@code itembank} (Spring Modulith boundary),
 * mirroring how {@code attempt.PinnedItem}/{@code scoring.ScoringAnswer}
 * already carry task type as a String across module lines. Values must match
 * {@code PteTaskType}/{@code PteSection} enum names; callers that need the
 * real enum parse it themselves (e.g. {@code PteTaskType.valueOf(...)}).
 *
 * <p>A weight of {@code null} or {@code 0} means "does not contribute to
 * that skill" (FR-02). Weights are percentages on the same 0-100-ish scale
 * as the APEUni V5 table and are NOT required to sum to 100 per skill/column
 * (the source table itself doesn't, due to rounding) — see
 * {@code ScoreTemplateActivationValidator} for what IS enforced.
 */
@Entity
@Table(name = "score_template_items", indexes = {
        @Index(name = "idx_score_template_items_template", columnList = "template_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ScoreTemplateItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ScoreTemplate template;

    @Column(nullable = false)
    private String taskType;

    @Column(nullable = false)
    private String section;

    @Column(nullable = false)
    private int sequence;

    @Column(nullable = false)
    private int minCount;

    @Column(nullable = false)
    private int maxCount;

    @Column(nullable = false)
    private int prepSeconds;

    @Column(nullable = false)
    private int responseSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScoringMethod scoringMethod;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal overallWeight = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal speakingWeight = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal writingWeight = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal readingWeight = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal listeningWeight = BigDecimal.ZERO;

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

    public void pinRuntimeProfile(TaskRuntimeProfileDescriptor profile) {
        this.runtimeProfileKey = profile.profileKey();
        this.runtimeProfileVersion = profile.profileVersion();
        this.runtimeBehaviorKey = profile.behaviorKey();
        this.runtimeRendererKey = profile.rendererKey();
        this.runtimeAnswerSchemaVersion = profile.answerSchemaVersion();
        this.runtimeScoringProfileKey = profile.scoringProfileKey();
        this.runtimeScoringProfileVersion = profile.scoringProfileVersion();
        this.runtimeRequiredClientCapabilities = String.join(",", profile.requiredClientCapabilities());
        this.runtimeProfileStatus = profile.status();
    }

    public TaskRuntimeProfileDescriptor pinnedRuntimeProfile() {
        if (runtimeProfileKey == null || runtimeProfileVersion == null || runtimeBehaviorKey == null
                || runtimeRendererKey == null || runtimeAnswerSchemaVersion == null
                || runtimeScoringProfileKey == null || runtimeScoringProfileVersion == null
                || runtimeProfileStatus == null) {
            return null;
        }
        List<String> capabilities = runtimeRequiredClientCapabilities == null
                || runtimeRequiredClientCapabilities.isBlank()
                ? List.of()
                : Arrays.stream(runtimeRequiredClientCapabilities.split(",")).toList();
        return new TaskRuntimeProfileDescriptor(taskType, runtimeProfileKey, runtimeProfileVersion,
                runtimeBehaviorKey, runtimeRendererKey, runtimeAnswerSchemaVersion, runtimeScoringProfileKey,
                runtimeScoringProfileVersion, capabilities, runtimeProfileStatus);
    }
}
