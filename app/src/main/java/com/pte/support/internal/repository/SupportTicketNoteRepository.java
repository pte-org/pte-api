package com.pte.support.internal.repository;

import com.pte.support.domain.SupportTicketNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportTicketNoteRepository extends JpaRepository<SupportTicketNote, Long> {

    List<SupportTicketNote> findByTicketPublicIdOrderByCreatedAtAsc(UUID ticketPublicId);
}
