package com.pte.support.domain;

import com.pte.shared.domain.BaseEntity;
import com.pte.support.domain.enums.TicketCategory;
import com.pte.support.domain.enums.TicketEntityType;
import com.pte.support.domain.enums.TicketStatus;
import com.pte.support.internal.exception.InvalidStatusTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "support_ticket", indexes = {
        @Index(name = "idx_support_ticket_tenant_status_created", columnList = "tenant_id, status, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class SupportTicket extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID submitterUserPublicId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TicketCategory category;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TicketStatus status = TicketStatus.OPEN;

    @Column(length = 32)
    @Enumerated(EnumType.STRING)
    private TicketEntityType entityType;

    @Column(length = 36)
    private String entityId;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    public void startProcessing() {
        if (status != TicketStatus.OPEN) {
            throw new InvalidStatusTransitionException(status, TicketStatus.IN_PROGRESS);
        }
        this.status = TicketStatus.IN_PROGRESS;
    }

    public void resolve() {
        if (status != TicketStatus.IN_PROGRESS) {
            throw new InvalidStatusTransitionException(status, TicketStatus.RESOLVED);
        }
        this.status = TicketStatus.RESOLVED;
    }
}
