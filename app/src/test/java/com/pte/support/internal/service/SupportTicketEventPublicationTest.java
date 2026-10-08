package com.pte.support.internal.service;

import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.support.domain.SupportTicket;
import com.pte.support.domain.SupportTicketNote;
import com.pte.support.domain.enums.TicketCategory;
import com.pte.support.domain.enums.TicketStatus;
import com.pte.support.dto.event.SupportTicketNoteAddedEvent;
import com.pte.support.dto.event.SupportTicketStatusChangedEvent;
import com.pte.support.dto.event.SupportTicketSubmittedEvent;
import com.pte.support.internal.dto.request.AddNoteRequest;
import com.pte.support.internal.dto.request.SubmitTicketRequest;
import com.pte.support.internal.dto.request.UpdateTicketStatusRequest;
import com.pte.support.internal.exception.InvalidStatusTransitionException;
import com.pte.support.internal.repository.SupportTicketNoteRepository;
import com.pte.support.internal.repository.SupportTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SupportTicketEventPublicationTest {
    @Mock
    private SupportTicketRepository ticketRepository;
    @Mock
    private SupportTicketNoteRepository noteRepository;
    @Mock
    private EntityReferenceValidator entityReferenceValidator;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SupportTicketService service;

    @BeforeEach
    void setUp() {
        service = new SupportTicketService(ticketRepository, noteRepository, entityReferenceValidator,
                auditLogService, eventPublisher);
    }

    @Test
    void submitPublishesEventWithPersistedTicketIdentity() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        SupportTicket saved = ticket(tenantId, caller.userId());
        when(ticketRepository.save(any(SupportTicket.class))).thenReturn(saved);

        service.submit(new SubmitTicketRequest(TicketCategory.BUG, "Broken item", null, null), caller);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new SupportTicketSubmittedEvent(
                saved.getPublicId(), tenantId, caller.userId(), TicketCategory.BUG));
    }

    @Test
    void invalidStatusTransitionPublishesNothing() {
        UUID tenantId = UUID.randomUUID();
        SupportTicket ticket = ticket(tenantId, UUID.randomUUID());
        ticket.setStatus(TicketStatus.RESOLVED);
        when(ticketRepository.findByPublicId(ticket.getPublicId())).thenReturn(java.util.Optional.of(ticket));

        assertThatThrownBy(() -> service.updateStatus(ticket.getPublicId(),
                new UpdateTicketStatusRequest(TicketStatus.IN_PROGRESS),
                new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_MANAGER"))))
                .isInstanceOf(InvalidStatusTransitionException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void notePublishesPersistedNoteIdentity() {
        UUID tenantId = UUID.randomUUID();
        UUID submitter = UUID.randomUUID();
        SupportTicket ticket = ticket(tenantId, submitter);
        SupportTicketNote note = new SupportTicketNote();
        note.setPublicId(UUID.randomUUID());
        when(ticketRepository.findByPublicId(ticket.getPublicId())).thenReturn(java.util.Optional.of(ticket));
        when(noteRepository.save(any(SupportTicketNote.class))).thenReturn(note);
        when(noteRepository.findByTicketPublicIdOrderByCreatedAtAsc(ticket.getPublicId())).thenReturn(List.of(note));

        service.addNote(ticket.getPublicId(), new AddNoteRequest("Private raw note"),
                new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_MANAGER")));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new SupportTicketNoteAddedEvent(
                ticket.getPublicId(), tenantId, submitter, note.getPublicId(), TicketCategory.BUG));
    }

    private SupportTicket ticket(UUID tenantId, UUID submitter) {
        SupportTicket ticket = new SupportTicket();
        ticket.setPublicId(UUID.randomUUID());
        ticket.setTenantId(tenantId);
        ticket.setSubmitterUserPublicId(submitter);
        ticket.setCategory(TicketCategory.BUG);
        ticket.setDescription("Broken item");
        return ticket;
    }
}
