package com.pte.scheduling.domain;

import com.pte.common.domain.BaseEntity;
import com.pte.scheduling.domain.enums.SessionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A scheduled exam window, referencing a published authoring snapshot by
 * {@code publicId} (never a cross-service join — §3). Its
 * {@link SessionComposition} selects which of the snapshot's task types are
 * actually delivered (full mock = all; practice = a host-chosen subset).
 */
@Entity
@Table(name = "exam_sessions")
@Getter
@Setter
@NoArgsConstructor
public class ExamSession extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID snapshotPublicId;

    @Column(nullable = false)
    private Instant opensAt;

    @Column(nullable = false)
    private Instant closesAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.SCHEDULED;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<SessionComposition> composition = new ArrayList<>();

    public void addCompositionItem(SessionComposition item) {
        item.setSession(this);
        composition.add(item);
    }

    public void open() {
        this.status = SessionStatus.OPEN;
    }

    public void close() {
        this.status = SessionStatus.CLOSED;
    }
}
