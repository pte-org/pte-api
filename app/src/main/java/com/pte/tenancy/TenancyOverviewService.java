package com.pte.tenancy;

import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-only platform tenant figures for the admin dashboard. Self-contained: it uses only this
 * module's own repository, so it adds no dependency to {@code tenancy}. Every read covers all
 * tenants (platform dashboards only) and ignores soft-deleted tenants.
 */
@Service
public class TenancyOverviewService {

    private final OrganizationRepository organizationRepository;

    public TenancyOverviewService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    /** Tenants by status and organization type (counts only, deleted excluded). */
    @Transactional(readOnly = true)
    public PlatformTenantCounts platformTenantCounts() {
        long active = 0L;
        long suspended = 0L;
        for (Object[] row : organizationRepository.countGroupedByStatus()) {
            long count = ((Number) row[1]).longValue();
            if (row[0] == OrganizationStatus.ACTIVE) {
                active = count;
            } else if (row[0] == OrganizationStatus.SUSPENDED) {
                suspended = count;
            }
        }
        Map<String, Long> byType = new TreeMap<>();
        for (Object[] row : organizationRepository.countGroupedByOrganizationType()) {
            byType.put((String) row[0], ((Number) row[1]).longValue());
        }
        return new PlatformTenantCounts(active, suspended, Collections.unmodifiableMap(byType));
    }

    /** Tenants created in {@code [from, to)}. */
    @Transactional(readOnly = true)
    public long countTenantsCreatedBetween(Instant from, Instant to) {
        return organizationRepository.countCreatedBetween(from, to);
    }

    /**
     * Tenants created per consecutive range: {@code boundaries} holds n+1 ascending instants and the
     * result holds n counts, one per {@code [boundaries[i], boundaries[i+1])}.
     */
    @Transactional(readOnly = true)
    public List<Long> countTenantsCreatedPerRange(List<Instant> boundaries) {
        if (boundaries == null || boundaries.size() < 2) {
            return List.of();
        }
        List<Long> counts = new ArrayList<>(boundaries.size() - 1);
        for (int i = 0; i < boundaries.size() - 1; i++) {
            counts.add(organizationRepository.countCreatedBetween(boundaries.get(i), boundaries.get(i + 1)));
        }
        return counts;
    }

    /** The {@code limit} newest tenants, newest first. */
    @Transactional(readOnly = true)
    public List<RecentTenantView> recentTenants(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Sort newestFirst = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        return organizationRepository.findRecent(PageRequest.of(0, limit, newestFirst)).stream()
                .map(tenant -> new RecentTenantView(tenant.getPublicId(), tenant.getCode(), tenant.getName(),
                        tenant.getOrganizationType(), tenant.getStatus().name(), tenant.getStudentLimit(),
                        tenant.getCreatedAt()))
                .toList();
    }

    /** Every non-deleted tenant's seat limit in one query (joined with student counts by the caller). */
    @Transactional(readOnly = true)
    public List<TenantSeatLimit> seatLimits() {
        return organizationRepository.findAllSeatLimits();
    }
}
