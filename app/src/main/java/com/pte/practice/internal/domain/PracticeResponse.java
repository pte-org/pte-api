package com.pte.practice.internal.domain;

import com.pte.attempt.ResponseConfidence;
import com.pte.practice.internal.domain.enums.PracticeScoringStatus;
import com.pte.practice.internal.domain.enums.PracticeResponseStatus;
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
 * renderer contract, a safe question snapshot and the student's draft/answer.
 * Restricted reference material is pinned for backend-only post-submit
 * evaluation and is never mapped into the student delivery response.
 */
@Entity
@Table(name = "practice_responses", uniqueConstraints = {
        @UniqueConstraint(name = "uk_practice_session_item_order", columnNames = {
                "practice_session_id", "order_index"
        })
}, indexes = {
        @Index(name = "idx_practice_responses_session", columnList = "practice_session_id"),
        @Index(name = "idx_practice_responses_status", columnList = "practice_session_id,status")
})
@Getter
@Setter
@NoArgsConstructor
public class PracticeResponse extends BaseEntity {

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

    /** Backend-only option snapshot retaining answer keys for deterministic scoring. */
    @Column(name = "objective_options_json", columnDefinition = "text")
    private String objectiveOptionsJson;

    @Column(name = "min_word_count")
    private Integer minWordCount;

    @Column(name = "max_word_count")
    private Integer maxWordCount;

    @Column(name = "audio_prompt_ref")
    private UUID audioPromptRef;

    @Column(name = "image_prompt_ref")
    private UUID imagePromptRef;

    /** Restricted answer material pinned for post-submit evaluation only. */
    @Column(name = "reference_answer_text", columnDefinition = "text")
    private String referenceAnswerText;

    @Column(name = "correct_answer_text", columnDefinition = "text")
    private String correctAnswerText;

    /** Deterministic result for objective tasks; AI feedback never overwrites it. */
    @Column(name = "objective_score")
    private Integer objectiveScore;

    @Column(name = "objective_evaluated_at")
    private Instant objectiveEvaluatedAt;

    /** Provider result used for local practice scoring validation and review provenance. */
    @Column(name = "ai_score")
    private Integer aiScore;

    @Column(name = "ai_score_status", length = 32)
    private String aiScoreStatus;

    @Column(name = "ai_sub_scores_json", columnDefinition = "text")
    private String aiSubScoresJson;

    @Column(name = "ai_provider", length = 64)
    private String aiProvider;

    @Column(name = "ai_model", length = 160)
    private String aiModel;

    @Column(name = "ai_provider_version", length = 100)
    private String aiProviderVersion;

    @Column(name = "ai_transcript", columnDefinition = "text")
    private String aiTranscript;

    @Column(name = "ai_detail_json", columnDefinition = "text")
    private String aiDetailJson;

    @Column(name = "ai_scored_at")
    private Instant aiScoredAt;

    @Column(name = "ai_result_hash", length = 64)
    private String aiResultHash;

    @Column(name = "ai_feedback", columnDefinition = "text")
    private String aiFeedback;

    /** Scoring lifecycle shared by every route; the worker only writes a result for the current dispatch token. */
    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_status", nullable = false, length = 16)
    private PracticeScoringStatus scoringStatus = PracticeScoringStatus.NONE;

    @Column(name = "scoring_dispatch_token")
    private UUID scoringDispatchToken;

    /** Max-age base: set when the answer is submitted or a retry is accepted. */
    @Column(name = "scoring_requested_at")
    private Instant scoringRequestedAt;

    /** Stale base: set on every dispatch, including a re-dispatch. */
    @Column(name = "scoring_dispatched_at")
    private Instant scoringDispatchedAt;

    @Column(name = "scoring_attempts", nullable = false)
    private int scoringAttempts;

    @Column(name = "scoring_redispatch_count", nullable = false)
    private int scoringRedispatchCount;

    @Column(name = "scoring_failure_code", length = 32)
    private String scoringFailureCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PracticeResponseStatus status = PracticeResponseStatus.PENDING;

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
