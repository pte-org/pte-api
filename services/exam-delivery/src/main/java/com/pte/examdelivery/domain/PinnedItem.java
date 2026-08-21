package com.pte.examdelivery.domain;

import com.pte.common.domain.BaseEntity;
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

    @Column(nullable = false)
    private String taskType;

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
}
