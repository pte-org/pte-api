package com.pte.itembank.domain;

import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.domain.enums.Visibility;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single authored PTE item. Type-specific fields are nullable and validated per
 * {@link PteTaskType} at save time. Media prompts are referenced by the media
 * module's {@code publicId} (UUID), never a cross-module FK. All items belong
 * to the platform bank and have a null {@code tenantId}.
 */
@Entity
@Table(name = "questions", indexes = {
        @Index(name = "idx_questions_tenant", columnList = "tenant_id"),
        @Index(name = "idx_questions_visibility", columnList = "visibility"),
        @Index(name = "idx_questions_task_type", columnList = "pte_task_type")
})
@Getter
@Setter
@NoArgsConstructor
public class Question extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private PteTaskType pteTaskType;

    /** Canonical identity for standard and custom authored questions. */
    @Column(name = "task_type_key", nullable = false, length = 64)
    private String taskTypeKey;

    @Column(name = "task_type_section", nullable = false, length = 16)
    private String taskTypeSection;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility;

    @Column
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionStatus status = QuestionStatus.DRAFT;

    /** Stable logical group shared by all revisions of one authored question. */
    @Column(name = "revision_group_public_id", nullable = false)
    private UUID revisionGroupPublicId;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber = 1;

    @Column(name = "supersedes_public_id")
    private UUID supersedesPublicId;

    @Column(name = "is_current", nullable = false)
    private boolean current = true;

    @jakarta.persistence.Version
    @Column(nullable = false)
    private long version;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

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

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<QuestionOption> options = new ArrayList<>();

    /** Add an option, maintaining the back-reference invariant (no public collection setter). */
    public void addOption(QuestionOption option) {
        option.setQuestion(this);
        options.add(option);
    }

    public boolean isShared() {
        return visibility == Visibility.SHARED;
    }
}
