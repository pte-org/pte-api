package com.pte.session.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "exam_forms", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"session_id", "form_index"})
})
@Getter
@Setter
@NoArgsConstructor
public class ExamForm extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamSession session;

    @Column(name = "snapshot_public_id", nullable = false)
    private java.util.UUID snapshotPublicId;

    @Column(name = "form_index", nullable = false)
    private int formIndex;

    @Column(name = "form_seed", nullable = false)
    private long formSeed;

    @Column(name = "algorithm_version", nullable = false, length = 32)
    private String algorithmVersion;

    @Column(name = "pool_policy_fingerprint", nullable = false, length = 128)
    private String poolPolicyFingerprint;
}
