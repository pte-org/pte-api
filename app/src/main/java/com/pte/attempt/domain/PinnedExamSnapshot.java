package com.pte.attempt.domain;

import com.pte.shared.domain.BaseEntity;
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
 * The self-contained, deep-copied snapshot+composition pinned into this
 * module's own tables at attempt creation. After this entity exists, the
 * attempt has zero runtime dependency on assessment/session for the rest of
 * its lifecycle — the whole reason attempt survives an issue with either
 * mid-exam, and (in the monolith) the reason the rest of an attempt's
 * lifecycle needs zero in-process calls to another module either.
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

    /** Copied once from the source snapshot at pin time (spec FR-14) — the score template this attempt's timing/scoring/weights are read from for its entire lifetime. */
    @Column(nullable = false)
    private UUID scoreTemplatePublicId;

    /** Additive provenance for the immutable scoring/weight contract; null only for legacy attempts. */
    @Column
    private Integer scoreTemplateVersion;

    @Column(nullable = false)
    private UUID tenantId;

    /**
     * Session-level {@code ExamPolicy}, copied once at pin time — never
     * re-read from session for this attempt's lifetime. String/Integer
     * fields, not attempt-local enums, matching the cross-module value
     * convention already used for {@code taskType}/{@code section}.
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

    @Column(length = 20)
    private String lockdownMode;

    @OneToMany(mappedBy = "pinnedSnapshot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<PinnedItem> items = new ArrayList<>();

    public void addItem(PinnedItem item) {
        item.setPinnedSnapshot(this);
        items.add(item);
    }
}
