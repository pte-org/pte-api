package com.pte.examdelivery.domain;

import com.pte.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The self-contained, deep-copied snapshot+composition pinned into THIS
 * service's own database at attempt creation (ADR-002). After this entity
 * exists, the attempt has zero dependency on authoring/scheduling for the rest
 * of its lifecycle — this is what makes exam-delivery survive an authoring/
 * scheduling outage mid-exam.
 */
@Entity
@Table(name = "pinned_exam_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class PinnedExamSnapshot extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    private ExamAttempt attempt;

    @Column(nullable = false)
    private UUID sourceSnapshotPublicId;

    @Column(nullable = false)
    private UUID sourceSessionPublicId;

    @Column(nullable = false)
    private UUID tenantId;

    /**
     * Session-level {@code ExamPolicy}, copied once at pin time — never
     * re-fetched from scheduling for this attempt's lifetime (ADR invariant:
     * runtime dependencies only flow INTO exam-delivery via event, never out).
     * String/Integer fields, not exam-delivery-local enums, matching the
     * cross-service value convention already used for {@code taskType}/{@code section}.
     */
    @Column(nullable = false)
    private String replayPolicyType;

    @Column
    private Integer replayPolicyLimit;

    @Column(nullable = false)
    private boolean deviceCheckRequired;

    @Column(nullable = false)
    private boolean proctorRequired;

    @Column(nullable = false)
    private String answerIntegrityLevel;

    @OneToMany(mappedBy = "pinnedSnapshot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<PinnedItem> items = new ArrayList<>();

    public void addItem(PinnedItem item) {
        item.setPinnedSnapshot(this);
        items.add(item);
    }
}
