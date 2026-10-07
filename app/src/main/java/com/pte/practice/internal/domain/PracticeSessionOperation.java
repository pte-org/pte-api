package com.pte.practice.internal.domain;

import com.pte.practice.internal.domain.enums.PracticeSessionOperationType;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Durable idempotency receipt for a practice-session mutation. */
@Entity
@Table(name = "practice_session_operations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_practice_session_operation_key", columnNames = {
                "practice_session_id", "operation_type", "idempotency_key"
        })
}, indexes = {
        @Index(name = "idx_practice_session_operations_session", columnList = "practice_session_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PracticeSessionOperation extends BaseEntity {

    @Column(name = "practice_session_id", nullable = false)
    private Long practiceSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 16)
    private PracticeSessionOperationType operationType;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
}
