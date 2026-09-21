package com.pte.itembank.domain;

import com.pte.itembank.domain.enums.TaskRuntimeProfileStatus;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Immutable allowlisted runtime profile persisted for historical resolution. */
@Entity
@Table(name = "task_runtime_profiles", indexes = {
        @Index(name = "idx_task_runtime_profiles_task_status", columnList = "task_type_code, status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "ux_task_runtime_profiles_task_version",
                columnNames = {"task_type_code", "profile_version"})
})
@Getter
@Setter
@NoArgsConstructor
public class TaskRuntimeProfile extends BaseEntity {

    @Column(name = "task_type_code", nullable = false, length = 64)
    private String taskTypeCode;

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

    @Column(name = "required_client_capabilities", nullable = false, length = 512)
    private String requiredClientCapabilities;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskRuntimeProfileStatus status = TaskRuntimeProfileStatus.ACTIVE;
}
