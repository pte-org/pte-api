package com.pte.support;

import com.pte.support.domain.enums.TicketStatus;
import com.pte.support.internal.repository.SupportTicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only support ticket counts for dashboards. Self-contained: it uses only this
 * module's own repository, so it adds no dependency to {@code support}. Counts are
 * tenant-scoped and ignore soft-deleted tickets.
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

    @Transactional(readOnly = true)
    public TicketCounts ticketCounts(UUID tenantId) {
        Map<TicketStatus, Long> byStatus = new EnumMap<>(TicketStatus.class);
        for (Object[] row : ticketRepository.countByTenantGroupedByStatus(tenantId)) {
            byStatus.put((TicketStatus) row[0], ((Number) row[1]).longValue());
        }
        return new TicketCounts(
                byStatus.getOrDefault(TicketStatus.OPEN, 0L),
                byStatus.getOrDefault(TicketStatus.IN_PROGRESS, 0L),
                byStatus.getOrDefault(TicketStatus.RESOLVED, 0L));
    }
}
