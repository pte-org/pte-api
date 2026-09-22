package com.pte.itembank.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Release-owned semantic capability contract for one screen/version pair. */
@Entity
@Table(name = "task_runtime_contracts", indexes = {
        @Index(name = "idx_task_runtime_contracts_status", columnList = "status, screen_key")
}, uniqueConstraints = {
        @UniqueConstraint(name = "ux_task_runtime_contract_screen_version",
                columnNames = {"screen_key", "contract_version"})
})
@Getter
@Setter
@NoArgsConstructor
public class TaskRuntimeContract extends BaseEntity {

    @Column(name = "screen_key", nullable = false, length = 96)
    private String screenKey;

    @Column(name = "contract_version", nullable = false)
    private int contractVersion;

    @Column(name = "profile_key", nullable = false, length = 96)
    private String profileKey;

    @Column(name = "profile_version", nullable = false)
    private int profileVersion;

    @Column(name = "behavior_key", nullable = false, length = 64)
    private String behaviorKey;

    @Column(name = "renderer_key", nullable = false, length = 96)
    private String rendererKey;

    @Column(name = "answer_schema_version", nullable = false)
    private int answerSchemaVersion;

    @Column(name = "scoring_profile_key", nullable = false, length = 64)
    private String scoringProfileKey;

    @Column(name = "scoring_profile_version", nullable = false)
    private int scoringProfileVersion;

    @Column(name = "scoring_mode", nullable = false, length = 16)
    private String scoringMode;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "required_client_capabilities", columnDefinition = "text[]", nullable = false)
    private String[] requiredClientCapabilities = new String[0];

    @Column(name = "min_supported_app_version", length = 32)
    private String minSupportedAppVersion;

    @Column(name = "authoring_contract_key", nullable = false, length = 96)
    private String authoringContractKey;

    @Column(name = "authoring_contract_version", nullable = false)
    private int authoringContractVersion;

    @Column(name = "requires_audio_prompt", nullable = false)
    private boolean requiresAudioPrompt;

    @Column(name = "requires_image_prompt", nullable = false)
    private boolean requiresImagePrompt;

    @Column(name = "requires_prompt_text", nullable = false)
    private boolean requiresPromptText;

    @Column(name = "requires_options", nullable = false)
    private boolean requiresOptions;

    @Column(name = "requires_correct_answer", nullable = false)
    private boolean requiresCorrectAnswer;

    @Column(name = "requires_word_count", nullable = false)
    private boolean requiresWordCount;

    @Column(name = "requires_single_correct_option", nullable = false)
    private boolean requiresSingleCorrectOption;

    @Column(name = "uses_option_order_as_correct_position", nullable = false)
    private boolean usesOptionOrderAsCorrectPosition;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";
}
