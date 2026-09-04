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

    /** Null = inherit the pinned session-level replay policy; non-null always wins (Phase 3/6 precedence rule). */
    @Column
    private Integer maxPlayCountOverride;

    /** Resolved once from `media` at StartAttempt pin time; LISTENING-section items only. Never re-fetched. */
    @Column(columnDefinition = "text")
    private String audioUrl;

    @Column
    private Instant audioUrlExpiresAt;

    /**
     * Non-null only for the 5 audio-prompt Speaking task types — the
     * client-facing sub-stage split lengths that {@code AudioListeningPrepCard}/
     * {@code RecordedAnswerPrepCard} used to hardcode locally, now server-owned
     * so a dynamic {@link #prepSeconds} (preListenSeconds + real audio duration
     * + preRecordSeconds) is possible. Every other task type leaves both null
     * (plans/phat-speaking-dynamic-prep-timing).
     */
    @Column
    private Integer preListenSeconds;

    @Column
    private Integer preRecordSeconds;

    /**
     * Resolved once from `media` at StartAttempt pin time, exactly like
     * {@link #audioUrl} — but unlike audio (served via a separate on-demand
     * `/audio` endpoint that reads this entity directly, never reaching the
     * cache/response layer), {@code imageUrl} is threaded all the way into
     * the student-facing {@code TaskView} response, since a static image has
     * no replay-limit/idempotency concern to gate behind an endpoint
     * (plans/phat-describe-image-e2e). Non-null only when {@link #imagePromptRef}
     * is non-null (mandatory for DESCRIBE_IMAGE, optional/absent elsewhere).
     */
    @Column(columnDefinition = "text")
    private String imageUrl;

    @Column
    private Instant imageUrlExpiresAt;
}
