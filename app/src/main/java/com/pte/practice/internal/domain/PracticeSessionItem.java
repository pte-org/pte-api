package com.pte.practice.internal.domain;

import com.pte.attempt.ResponseConfidence;
import com.pte.practice.internal.domain.enums.PracticeSessionItemStatus;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Student-owned task boundary for a practice session. It stores only the
 * renderer contract, a safe question snapshot and the student's draft/answer;
 * correct-answer data is deliberately excluded from this aggregate.
 */
@Entity
@Table(name = "practice_session_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_practice_session_item_order", columnNames = {
                "practice_session_id", "order_index"
        })
}, indexes = {
        @Index(name = "idx_practice_session_items_session", columnList = "practice_session_id"),
        @Index(name = "idx_practice_session_items_status", columnList = "practice_session_id,status")
})
@Getter
@Setter
@NoArgsConstructor
public class PracticeSessionItem extends BaseEntity {

    @Column(name = "practice_session_id", nullable = false)
    private Long practiceSessionId;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "task_code", nullable = false, length = 64)
    private String taskCode;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(nullable = false, length = 32)
    private String section;

    @Column(name = "renderer_key", nullable = false, length = 128)
    private String rendererKey;

    @Column(name = "contract_version")
    private Integer contractVersion;

    @Column(name = "answer_schema_version")
    private Integer answerSchemaVersion;

    /** Source identity is retained for traceability, never returned to the student. */
    @Column(name = "source_question_public_id")
    private UUID sourceQuestionPublicId;

    /** Safe question content pinned when the session begins. */
    @Column(name = "prompt_text", columnDefinition = "text")
    private String promptText;

    @Column(name = "options_json", columnDefinition = "text")
    private String optionsJson;

    @Column(name = "min_word_count")
    private Integer minWordCount;

    @Column(name = "max_word_count")
    private Integer maxWordCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PracticeSessionItemStatus status = PracticeSessionItemStatus.PENDING;

    @Column(name = "saved_payload", columnDefinition = "text")
    private String savedPayload;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ResponseConfidence confidence;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "skipped_at")
    private Instant skippedAt;

    @Version
    private Long version;
}
