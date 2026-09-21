package com.pte.session.domain;

import com.pte.session.domain.enums.AudienceDecisionReason;
import com.pte.session.domain.enums.AudienceMemberStatus;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_audience_members", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"session_id", "student_public_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class ExamAudienceMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamSession session;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(name = "student_public_id", nullable = false)
    private UUID studentPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AudienceMemberStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 32)
    private AudienceDecisionReason reason;

    @Column(name = "source_summary", length = 1000)
    private String sourceSummary;

    @Column(name = "prior_session_public_id")
    private UUID priorSessionPublicId;

    @Column(name = "prior_session_name", length = 255)
    private String priorSessionName;

    @Column(name = "prior_status", length = 16)
    private String priorStatus;

    @Column(name = "published_at")
    private Instant publishedAt;
}
