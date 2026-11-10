package com.pte.itembank.internal.generation.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One version of one editable prompt. {@code content} is the JSON of a prompt (title, intro, sections); the text a
 * model receives is assembled from it. Transition rules live in the service; these methods only record the state the
 * service decided.
 */
@Entity
@Table(name = "ai_prompt_versions")
@Getter
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiPromptVersion extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 16)
    private AiPromptKind kind;

    @Column(name = "prompt_key", nullable = false, updatable = false, length = 64)
    private String promptKey;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AiPromptStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "author_user_public_id", updatable = false)
    private UUID authorUserPublicId;

    @Column(name = "updated_by_user_public_id")
    private UUID updatedByUserPublicId;

    @Column(name = "approver_user_public_id")
    private UUID approverUserPublicId;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "test_passed")
    private Boolean testPassed;

    @Column(name = "tested_content_hash", length = 64)
    private String testedContentHash;

    @Column(name = "test_note", length = 500)
    private String testNote;

    @Column(name = "tested_by_user_public_id")
    private UUID testedByUserPublicId;

    @Column(name = "tested_at")
    private Instant testedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    /** The first version of a prompt, live at once: the content the application shipped with. */
    public static AiPromptVersion seeded(AiPromptKind kind, String promptKey, String contentJson, String contentHash,
                                         Instant now) {
        AiPromptVersion version = new AiPromptVersion();
        version.kind = kind;
        version.promptKey = promptKey;
        version.versionNo = 1;
        version.content = contentJson;
        version.contentHash = contentHash;
        version.status = AiPromptStatus.ACTIVE;
        version.activatedAt = now;
        return version;
    }

    public static AiPromptVersion draft(AiPromptKind kind, String promptKey, int versionNo, String contentJson,
                                        String contentHash, UUID authorUserPublicId) {
        AiPromptVersion version = new AiPromptVersion();
        version.kind = kind;
        version.promptKey = promptKey;
        version.versionNo = versionNo;
        version.content = contentJson;
        version.contentHash = contentHash;
        version.status = AiPromptStatus.DRAFT;
        version.authorUserPublicId = authorUserPublicId;
        version.updatedByUserPublicId = authorUserPublicId;
        return version;
    }

    /** New content for a draft (or a rejected version, which goes back to draft); any earlier test no longer counts. */
    public void replaceContent(String contentJson, String newHash, UUID editorUserPublicId) {
        this.content = contentJson;
        this.contentHash = newHash;
        this.updatedByUserPublicId = editorUserPublicId;
        this.status = AiPromptStatus.DRAFT;
        this.rejectionReason = null;
        this.submittedAt = null;
        this.decidedAt = null;
    }

    public void recordTest(boolean passed, String note, UUID testerUserPublicId, Instant now) {
        this.testPassed = passed;
        this.testedContentHash = contentHash;
        this.testNote = note;
        this.testedByUserPublicId = testerUserPublicId;
        this.testedAt = now;
    }

    /** True when the last test passed on exactly the content the version has now. */
    public boolean hasPassingTest() {
        return Boolean.TRUE.equals(testPassed) && contentHash.equals(testedContentHash);
    }

    public void submit(Instant now) {
        this.status = AiPromptStatus.PENDING;
        this.submittedAt = now;
    }

    public void reject(UUID approverUserPublicId, String reason, Instant now) {
        this.status = AiPromptStatus.REJECTED;
        this.approverUserPublicId = approverUserPublicId;
        this.rejectionReason = reason;
        this.decidedAt = now;
    }

    public void activate(UUID approverUserPublicId, Instant now) {
        this.status = AiPromptStatus.ACTIVE;
        this.approverUserPublicId = approverUserPublicId;
        this.decidedAt = now;
        this.activatedAt = now;
    }

    /** Recorded when another version becomes the live one (the database row is changed by a bulk update). */
    public void retire(Instant now) {
        this.status = AiPromptStatus.RETIRED;
        this.retiredAt = now;
    }

    /** A discarded draft is hidden, not removed, so the version numbers it used stay unique. */
    public void discard() {
        setDeleted(true);
    }
}
