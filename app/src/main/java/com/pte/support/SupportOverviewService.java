package com.pte.support;

import com.pte.support.domain.enums.TicketStatus;
import com.pte.support.internal.repository.SupportTicketRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only support ticket counts for dashboards. Self-contained: it uses only this
 * module's own repository, so it adds no dependency to {@code support}. Tenant methods
 * are tenant-scoped; the platform methods read every tenant and are for platform
 * dashboards only. Soft-deleted tickets are ignored.
 */
@Service
public class SupportOverviewService {

    private final SupportTicketRepository ticketRepository;

    public SupportOverviewService(SupportTicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /** {@code resolvedAwaitingClose} counts tickets marked resolved but not yet closed. */
    public record TicketCounts(long open, long inProgress, long resolvedAwaitingClose) {
    }

    /**
     * Safe ticket reference for a dashboard list: identifiers, category name and age only. The
     * description, submitter and notes never leave support through this facade.
     */
    public record OpenTicketBrief(UUID publicId, UUID tenantId, String category, Instant openedAt) {
    }

    @Transactional(readOnly = true)
    public TicketCounts ticketCounts(UUID tenantId) {
        return toCounts(ticketRepository.countByTenantGroupedByStatus(tenantId));
    }

    /** Platform-wide counts by workflow state (all tenants). */
    @Transactional(readOnly = true)
    public TicketCounts platformTicketCounts() {
        return toCounts(ticketRepository.countGroupedByStatus());
    }

    /** Tickets opened (created) in {@code [from, to)} across all tenants. */
    @Transactional(readOnly = true)
    public long countOpenedBetween(Instant from, Instant to) {
        return ticketRepository.countCreatedBetween(from, to);
    }

    /** The {@code limit} oldest tickets still in the OPEN state (not yet picked up), oldest first. */
    @Transactional(readOnly = true)
    public List<OpenTicketBrief> oldestOpenTickets(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return ticketRepository.findOldestByStatus(TicketStatus.OPEN, PageRequest.of(0, limit)).stream()
                .map(ticket -> new OpenTicketBrief(ticket.getPublicId(), ticket.getTenantId(),
                        ticket.getCategory().name(), ticket.getCreatedAt()))
                .toList();
    }

    private static TicketCounts toCounts(List<Object[]> rows) {
        Map<TicketStatus, Long> byStatus = new EnumMap<>(TicketStatus.class);
        for (Object[] row : rows) {
            byStatus.put((TicketStatus) row[0], ((Number) row[1]).longValue());
        }
        return new TicketCounts(
                byStatus.getOrDefault(TicketStatus.OPEN, 0L),
                byStatus.getOrDefault(TicketStatus.IN_PROGRESS, 0L),
                byStatus.getOrDefault(TicketStatus.RESOLVED, 0L));
    }
}
