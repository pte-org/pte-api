package com.pte.session.domain;

import com.pte.session.domain.enums.AudienceSourceType;
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

import java.util.UUID;

/** One audience source (a student, a class or a program) of a session; resolved into registrations on preflight and publish. */
@Entity
@Table(name = "exam_session_audience_sources", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"session_id", "source_type", "source_public_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class ExamSessionAudienceSource extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamSession session;

    @Column(nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private AudienceSourceType sourceType;

    @Column(name = "source_public_id", nullable = false)
    private UUID sourcePublicId;

    @Column(name = "created_by")
    private UUID createdBy;
}
