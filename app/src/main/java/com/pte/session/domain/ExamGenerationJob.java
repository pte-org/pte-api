package com.pte.session.domain;

import com.pte.session.domain.enums.GenerationJobStatus;
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

@Entity
@Table(name = "exam_generation_jobs", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"session_id", "idempotency_key"})
})
@Getter
@Setter
@NoArgsConstructor
public class ExamGenerationJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamSession session;

    @Column(nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GenerationJobStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "algorithm_version", nullable = false, length = 32)
    private String algorithmVersion;

    @Column(name = "base_seed", nullable = false)
    private long baseSeed;

    @Column(name = "forms_total", nullable = false)
    private int formsTotal;

    @Column(name = "forms_completed", nullable = false)
    private int formsCompleted;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
}
