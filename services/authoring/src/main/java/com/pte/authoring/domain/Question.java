package com.pte.authoring.domain;

import com.pte.authoring.domain.enums.PteTaskType;
import com.pte.authoring.domain.enums.QuestionStatus;
import com.pte.authoring.domain.enums.Visibility;
import com.pte.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
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
 * service's {@code publicId} (UUID), never a cross-service FK (§3). SHARED items
 * have a null {@code tenantId} (platform bank); PRIVATE items belong to one tenant.
 */
@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
public class Question extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PteTaskType pteTaskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility;

    @Column
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionStatus status = QuestionStatus.DRAFT;

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
