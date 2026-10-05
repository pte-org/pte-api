package com.pte.support.internal.service;

import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PagedResult;
import com.pte.support.domain.SupportTicket;
import com.pte.support.domain.SupportTicketNote;
import com.pte.support.domain.enums.TicketCategory;
import com.pte.support.domain.enums.TicketEntityType;
import com.pte.support.domain.enums.TicketStatus;
import com.pte.support.internal.constant.SupportConstants;
import com.pte.support.internal.dto.request.AddNoteRequest;
import com.pte.support.internal.dto.request.SubmitTicketRequest;
import com.pte.support.internal.dto.request.UpdateTicketStatusRequest;
import com.pte.support.internal.dto.response.SupportTicketResponse;
import com.pte.support.internal.dto.response.SupportTicketSummaryResponse;
import com.pte.support.internal.exception.EntityReferenceNotFoundException;
import com.pte.support.internal.exception.InvalidStatusTransitionException;
import com.pte.support.internal.exception.InvalidTicketEntityPairException;
import com.pte.support.internal.exception.SupportTicketNotFoundException;
import com.pte.support.internal.repository.SupportTicketNoteRepository;
import com.pte.support.internal.repository.SupportTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceTest {

    @Mock
    private SupportTicketRepository ticketRepository;

    @Mock
    private SupportTicketNoteRepository noteRepository;

    @Mock
    private EntityReferenceValidator entityReferenceValidator;

    @Mock
    private AuditLogService auditLogService;

    private SupportTicketService service;

    @BeforeEach
    void setUp() {
        service = new SupportTicketService(ticketRepository, noteRepository, entityReferenceValidator, auditLogService);
    }

    private CurrentUser caller(UUID tenantId) {
        return new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
    }

    private SupportTicket ticketFor(UUID tenantId) {
        SupportTicket ticket = new SupportTicket();
        ticket.setPublicId(UUID.randomUUID());
        ticket.setTenantId(tenantId);
        ticket.setSubmitterUserPublicId(UUID.randomUUID());
        ticket.setCategory(TicketCategory.BUG);
        ticket.setDescription("Something is broken");
        return ticket;
    }

    @Test
    void submit_validUnlinkedTicket_savesWithOpenStatus() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = caller(tenantId);
        SubmitTicketRequest request = new SubmitTicketRequest(TicketCategory.BUG, "Something is broken", null, null);

        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        SupportTicketResponse response = service.submit(request, caller);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.category()).isEqualTo(TicketCategory.BUG);
        assertThat(response.notes()).isEmpty();
        verify(ticketRepository).save(any(SupportTicket.class));
        verify(auditLogService).record(eq(caller), any(), any(), any(), any());
    }

    @Test
    void submit_validLinkedToQuestion_validatesAndSaves() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = caller(tenantId);
        String entityId = UUID.randomUUID().toString();
        SubmitTicketRequest request = new SubmitTicketRequest(TicketCategory.CONTENT_COMPLAINT, "Wrong answer",
                TicketEntityType.QUESTION, entityId);

        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        SupportTicketResponse response = service.submit(request, caller);

        verify(entityReferenceValidator).validate(TicketEntityType.QUESTION, entityId, caller);
        verify(ticketRepository).save(any(SupportTicket.class));
        assertThat(response.entityType()).isEqualTo(TicketEntityType.QUESTION);
        assertThat(response.entityId()).isEqualTo(entityId);
    }

    @Test
    void submit_entityIdNullEntityTypeSet_throwsInvalidPair() {
        CurrentUser caller = caller(UUID.randomUUID());
        SubmitTicketRequest request = new SubmitTicketRequest(TicketCategory.BUG, "desc",
                TicketEntityType.QUESTION, null);

        doThrow(new InvalidTicketEntityPairException())
                .when(entityReferenceValidator).validate(TicketEntityType.QUESTION, null, caller);

        assertThatThrownBy(() -> service.submit(request, caller))
                .isInstanceOf(InvalidTicketEntityPairException.class);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void submit_entityDoesNotExist_throwsEntityReferenceNotFound() {
        CurrentUser caller = caller(UUID.randomUUID());
        String entityId = UUID.randomUUID().toString();
        SubmitTicketRequest request = new SubmitTicketRequest(TicketCategory.BUG, "desc",
                TicketEntityType.EXAM_SESSION, entityId);

        doThrow(new EntityReferenceNotFoundException())
                .when(entityReferenceValidator).validate(TicketEntityType.EXAM_SESSION, entityId, caller);

        assertThatThrownBy(() -> service.submit(request, caller))
                .isInstanceOf(EntityReferenceNotFoundException.class);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listForHost_returnsOnlyCallerTenantTickets() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = caller(tenantId);
        SupportTicket ticket = ticketFor(tenantId);

        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ticket)));

        PagedResult<SupportTicketSummaryResponse> result = service.listForHost(null, null, 0, 10, caller);

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).publicId()).isEqualTo(ticket.getPublicId());
        verify(ticketRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getDetailForHost_differentTenantTicket_throwsNotFound() {
        UUID callerTenantId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        CurrentUser caller = caller(callerTenantId);

        when(ticketRepository.findByPublicIdAndTenantId(publicId, callerTenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetailForHost(publicId, caller))
                .isInstanceOf(SupportTicketNotFoundException.class);
    }

    @Test
    void getDetailForHost_ownTicket_returnsWithNotes() {
        UUID tenantId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        CurrentUser caller = caller(tenantId);
        SupportTicket ticket = ticketFor(tenantId);
        ticket.setPublicId(publicId);

        SupportTicketNote note = new SupportTicketNote();
        note.setPublicId(UUID.randomUUID());
        note.setTicketId(1L);
        note.setTicketPublicId(publicId);
        note.setAdminPublicId(UUID.randomUUID());
        note.setContent("Admin note content");

        when(ticketRepository.findByPublicIdAndTenantId(publicId, tenantId))
                .thenReturn(Optional.of(ticket));
        when(noteRepository.findByTicketPublicIdOrderByCreatedAtAsc(publicId))
                .thenReturn(List.of(note));

        SupportTicketResponse response = service.getDetailForHost(publicId, caller);

        assertThat(response.publicId()).isEqualTo(publicId);
        assertThat(response.notes()).hasSize(1);
        assertThat(response.notes().get(0).content()).isEqualTo("Admin note content");
    }

    @Test
    void closeForHost_openOwnTicket_closesAndAudits() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = caller(tenantId);
        SupportTicket ticket = ticketFor(tenantId);
        UUID publicId = ticket.getPublicId();

        when(ticketRepository.findByPublicIdAndTenantId(publicId, tenantId)).thenReturn(Optional.of(ticket));
        when(noteRepository.findByTicketPublicIdOrderByCreatedAtAsc(publicId)).thenReturn(List.of());

        SupportTicketResponse response = service.closeForHost(publicId, caller);

        assertThat(response.status()).isEqualTo(TicketStatus.CLOSED);
        verify(auditLogService).record(eq(caller), eq(SupportConstants.AGGREGATE_SUPPORT_TICKET),
                eq(publicId.toString()), eq(SupportConstants.EVENT_TICKET_CLOSED), any());
    }

    @Test
    void closeForHost_ticketAlreadyPickedUpByAdmin_throwsInvalidTransition() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = caller(tenantId);
        SupportTicket ticket = ticketFor(tenantId);
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        UUID publicId = ticket.getPublicId();

        when(ticketRepository.findByPublicIdAndTenantId(publicId, tenantId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.closeForHost(publicId, caller))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        verify(auditLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void closeForHost_differentTenantTicket_throwsNotFound() {
        UUID callerTenantId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        CurrentUser caller = caller(callerTenantId);

        when(ticketRepository.findByPublicIdAndTenantId(publicId, callerTenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.closeForHost(publicId, caller))
                .isInstanceOf(SupportTicketNotFoundException.class);
        verify(auditLogService, never()).record(any(), any(), any(), any(), any());
    }

    // --- Phase 3: Admin API tests ---

    @Test
    void updateStatus_openToInProgress_transitionsAndAudits() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser admin = caller(tenantId);
        SupportTicket ticket = ticketFor(tenantId);
        UUID publicId = ticket.getPublicId();

        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(ticket));
        when(noteRepository.findByTicketPublicIdOrderByCreatedAtAsc(publicId)).thenReturn(List.of());

        SupportTicketResponse response = service.updateStatus(publicId,
                new UpdateTicketStatusRequest(TicketStatus.IN_PROGRESS), admin);

        assertThat(response.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        verify(auditLogService).record(eq(admin), any(), any(), any(), any());
    }

    @Test
    void updateStatus_inProgressToResolved_transitionsAndAudits() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser admin = caller(tenantId);
        SupportTicket ticket = ticketFor(tenantId);
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        UUID publicId = ticket.getPublicId();

        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(ticket));
        when(noteRepository.findByTicketPublicIdOrderByCreatedAtAsc(publicId)).thenReturn(List.of());

        SupportTicketResponse response = service.updateStatus(publicId,
                new UpdateTicketStatusRequest(TicketStatus.RESOLVED), admin);

        assertThat(response.status()).isEqualTo(TicketStatus.RESOLVED);
        verify(auditLogService).record(eq(admin), any(), any(), any(), any());
    }

    @Test
    void updateStatus_resolvedToOpen_throwsInvalidTransition() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser admin = caller(tenantId);
        SupportTicket ticket = ticketFor(tenantId);
        ticket.setStatus(TicketStatus.RESOLVED);
        UUID publicId = ticket.getPublicId();

        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.updateStatus(publicId,
                new UpdateTicketStatusRequest(TicketStatus.IN_PROGRESS), admin))
                .isInstanceOf(InvalidStatusTransitionException.class);
        verify(auditLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void updateStatus_unknownTicketPublicId_throwsNotFound() {
        UUID publicId = UUID.randomUUID();
        CurrentUser admin = caller(UUID.randomUUID());

        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(publicId,
                new UpdateTicketStatusRequest(TicketStatus.IN_PROGRESS), admin))
                .isInstanceOf(SupportTicketNotFoundException.class);
    }

    @Test
    void addNote_savesNoteWithAdminPublicId() {
        UUID tenantId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        CurrentUser admin = new CurrentUser(adminUserId, tenantId, List.of("SYSTEM_ADMIN"));
        SupportTicket ticket = ticketFor(tenantId);
        ticket.setId(42L);
        UUID publicId = ticket.getPublicId();

        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(ticket));
        when(noteRepository.findByTicketPublicIdOrderByCreatedAtAsc(publicId)).thenReturn(List.of());

        service.addNote(publicId, new AddNoteRequest("Please fix ASAP"), admin);

        ArgumentCaptor<SupportTicketNote> captor = ArgumentCaptor.forClass(SupportTicketNote.class);
        verify(noteRepository).save(captor.capture());
        SupportTicketNote saved = captor.getValue();
        assertThat(saved.getAdminPublicId()).isEqualTo(adminUserId);
        assertThat(saved.getTicketId()).isEqualTo(42L);
        assertThat(saved.getTicketPublicId()).isEqualTo(publicId);
        assertThat(saved.getContent()).isEqualTo("Please fix ASAP");
    }

    @Test
    void addNote_multipleNotes_allPersisted() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser admin = caller(tenantId);
        SupportTicket ticket = ticketFor(tenantId);
        ticket.setId(7L);
        UUID publicId = ticket.getPublicId();

        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(ticket));
        when(noteRepository.findByTicketPublicIdOrderByCreatedAtAsc(publicId)).thenReturn(List.of());

        service.addNote(publicId, new AddNoteRequest("First note"), admin);
        service.addNote(publicId, new AddNoteRequest("Second note"), admin);

        verify(noteRepository, times(2)).save(any(SupportTicketNote.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAllForAdmin_noFilters_returnsAllTenants() {
        SupportTicket ticket1 = ticketFor(UUID.randomUUID());
        SupportTicket ticket2 = ticketFor(UUID.randomUUID());

        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ticket1, ticket2)));

        PagedResult<SupportTicketSummaryResponse> result = service.listAllForAdmin(null, null, null, 0, 10);

        assertThat(result.data()).hasSize(2);
        verify(ticketRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAllForAdmin_filterByTenantId_narrowsResults() {
        UUID tenantId = UUID.randomUUID();
        SupportTicket ticket = ticketFor(tenantId);

        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ticket)));

        PagedResult<SupportTicketSummaryResponse> result = service.listAllForAdmin(null, null, tenantId, 0, 10);

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).publicId()).isEqualTo(ticket.getPublicId());
        verify(ticketRepository).findAll(any(Specification.class), any(Pageable.class));
    }
}
