package com.pte.support.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "support_ticket_note", indexes = {
        @Index(name = "idx_support_ticket_note_ticket_created", columnList = "ticket_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class SupportTicketNote extends BaseEntity {

    @Column(nullable = false)
    private Long ticketId;

    @Column(nullable = false)
    private UUID ticketPublicId;

    @Column(nullable = false)
    private UUID adminPublicId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
}
