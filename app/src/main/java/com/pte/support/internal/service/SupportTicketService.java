package com.pte.support.internal.service;

import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import com.pte.support.domain.SupportTicket;
import com.pte.support.domain.SupportTicketNote;
import com.pte.support.domain.enums.TicketCategory;
import com.pte.support.domain.enums.TicketStatus;
import com.pte.support.dto.event.SupportTicketNoteAddedEvent;
import com.pte.support.dto.event.SupportTicketStatusChangedEvent;
import com.pte.support.dto.event.SupportTicketSubmittedEvent;
import com.pte.support.internal.constant.SupportConstants;
import com.pte.support.internal.dto.request.AddNoteRequest;
import com.pte.support.internal.dto.request.SubmitTicketRequest;
import com.pte.support.internal.dto.request.UpdateTicketStatusRequest;
import com.pte.support.internal.dto.response.SupportTicketResponse;
import com.pte.support.internal.dto.response.SupportTicketSummaryResponse;
import com.pte.support.internal.exception.SupportTicketNotFoundException;
import com.pte.support.internal.mapper.SupportTicketMapper;
import com.pte.support.internal.repository.SupportTicketNoteRepository;
import com.pte.support.internal.repository.SupportTicketRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SupportTicketService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketNoteRepository noteRepository;
    private final EntityReferenceValidator entityReferenceValidator;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    public SupportTicketService(SupportTicketRepository ticketRepository,
            SupportTicketNoteRepository noteRepository,
            EntityReferenceValidator entityReferenceValidator,
            AuditLogService auditLogService, ApplicationEventPublisher eventPublisher) {
        this.ticketRepository = ticketRepository;
        this.noteRepository = noteRepository;
        this.entityReferenceValidator = entityReferenceValidator;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SupportTicketResponse submit(SubmitTicketRequest request, CurrentUser caller) {
        entityReferenceValidator.validate(request.entityType(), request.entityId(), caller);

        SupportTicket ticket = new SupportTicket();
        ticket.setTenantId(caller.tenantId());
        ticket.setSubmitterUserPublicId(caller.userId());
        ticket.setCategory(request.category());
        ticket.setDescription(request.description());
        ticket.setEntityType(request.entityType());
        ticket.setEntityId(request.entityId());

        SupportTicket saved = ticketRepository.save(ticket);
        eventPublisher.publishEvent(new SupportTicketSubmittedEvent(saved.getPublicId(), saved.getTenantId(),
                saved.getSubmitterUserPublicId(), saved.getCategory()));
        auditLogService.record(caller, SupportConstants.AGGREGATE_SUPPORT_TICKET,
                saved.getPublicId().toString(), SupportConstants.EVENT_TICKET_SUBMITTED,
                "Submitted ticket [" + saved.getCategory() + "]");
        return SupportTicketMapper.toResponse(saved, List.of());
    }

    @Transactional(readOnly = true)
    public PagedResult<SupportTicketSummaryResponse> listForHost(TicketStatus status, TicketCategory category,
            int page, int size, CurrentUser caller) {
        int cappedSize = Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), cappedSize,
                Sort.by(Sort.Order.desc("createdAt")));
        Specification<SupportTicket> spec = byTenant(caller.tenantId())
                .and(byStatusOptional(status))
                .and(byCategoryOptional(category));
        Page<SupportTicket> result = ticketRepository.findAll(spec, pageRequest);
        List<SupportTicketSummaryResponse> data = result.getContent().stream()
                .map(SupportTicketMapper::toSummaryResponse).toList();
        return new PagedResult<>(data, new PageMeta(result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(),
                result.isFirst(), result.isLast(), result.hasNext(), result.hasPrevious()));
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse getDetailForHost(UUID publicId, CurrentUser caller) {
        SupportTicket ticket = ticketRepository.findByPublicIdAndTenantId(publicId, caller.tenantId())
                .orElseThrow(SupportTicketNotFoundException::new);
        List<SupportTicketNote> notes = noteRepository.findByTicketPublicIdOrderByCreatedAtAsc(ticket.getPublicId());
        return SupportTicketMapper.toResponse(ticket, notes);
    }

    @Transactional
    public SupportTicketResponse updateStatus(UUID publicId, UpdateTicketStatusRequest request, CurrentUser caller) {
        SupportTicket ticket = ticketRepository.findByPublicId(publicId)
                .orElseThrow(SupportTicketNotFoundException::new);
        TicketStatus previousStatus = ticket.getStatus();
        if (request.status() == TicketStatus.IN_PROGRESS) {
            ticket.startProcessing();
        } else if (request.status() == TicketStatus.RESOLVED) {
            ticket.resolve();
        } else {
            throw new com.pte.support.internal.exception.InvalidStatusTransitionException(ticket.getStatus(), request.status());
        }
        eventPublisher.publishEvent(new SupportTicketStatusChangedEvent(ticket.getPublicId(), ticket.getTenantId(),
                ticket.getSubmitterUserPublicId(), ticket.getCategory(), previousStatus, ticket.getStatus()));
        auditLogService.record(caller, SupportConstants.AGGREGATE_SUPPORT_TICKET,
                ticket.getPublicId().toString(), SupportConstants.EVENT_TICKET_STATUS_UPDATED,
                "Status updated to " + request.status());
        List<SupportTicketNote> notes = noteRepository.findByTicketPublicIdOrderByCreatedAtAsc(ticket.getPublicId());
        return SupportTicketMapper.toResponse(ticket, notes);
    }

    @Transactional
    public SupportTicketResponse addNote(UUID publicId, AddNoteRequest request, CurrentUser caller) {
        SupportTicket ticket = ticketRepository.findByPublicId(publicId)
                .orElseThrow(SupportTicketNotFoundException::new);
        SupportTicketNote note = new SupportTicketNote();
        note.setTicketId(ticket.getId());
        note.setTicketPublicId(ticket.getPublicId());
        note.setAdminPublicId(caller.userId());
        note.setContent(request.content());
        SupportTicketNote savedNote = noteRepository.save(note);
        eventPublisher.publishEvent(new SupportTicketNoteAddedEvent(ticket.getPublicId(), ticket.getTenantId(),
                ticket.getSubmitterUserPublicId(), savedNote.getPublicId(), ticket.getCategory()));
        auditLogService.record(caller, SupportConstants.AGGREGATE_SUPPORT_TICKET,
                ticket.getPublicId().toString(), SupportConstants.EVENT_TICKET_NOTE_ADDED,
                "Admin added note");
        List<SupportTicketNote> notes = noteRepository.findByTicketPublicIdOrderByCreatedAtAsc(ticket.getPublicId());
        return SupportTicketMapper.toResponse(ticket, notes);
    }

    @Transactional(readOnly = true)
    public PagedResult<SupportTicketSummaryResponse> listAllForAdmin(TicketStatus status, TicketCategory category,
            UUID tenantId, int page, int size) {
        int cappedSize = Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), cappedSize,
                Sort.by(Sort.Order.desc("createdAt")));
        Specification<SupportTicket> spec = byStatusOptional(status)
                .and(byCategoryOptional(category))
                .and(byTenantOptional(tenantId));
        Page<SupportTicket> result = ticketRepository.findAll(spec, pageRequest);
        List<SupportTicketSummaryResponse> data = result.getContent().stream()
                .map(SupportTicketMapper::toSummaryResponse).toList();
        return new PagedResult<>(data, new PageMeta(result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(),
                result.isFirst(), result.isLast(), result.hasNext(), result.hasPrevious()));
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse getDetailForAdmin(UUID publicId) {
        SupportTicket ticket = ticketRepository.findByPublicId(publicId)
                .orElseThrow(SupportTicketNotFoundException::new);
        List<SupportTicketNote> notes = noteRepository.findByTicketPublicIdOrderByCreatedAtAsc(ticket.getPublicId());
        return SupportTicketMapper.toResponse(ticket, notes);
    }

    private static Specification<SupportTicket> byTenant(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    private static Specification<SupportTicket> byTenantOptional(UUID tenantId) {
        return (root, query, cb) -> tenantId == null ? cb.conjunction() : cb.equal(root.get("tenantId"), tenantId);
    }

    private static Specification<SupportTicket> byStatusOptional(TicketStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    private static Specification<SupportTicket> byCategoryOptional(TicketCategory category) {
        return (root, query, cb) -> category == null ? cb.conjunction() : cb.equal(root.get("category"), category);
    }
}
