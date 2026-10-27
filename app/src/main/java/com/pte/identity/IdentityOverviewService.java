package com.pte.identity;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only counts for dashboards. Counts only: no user rows leave this module,
 * and every figure is scoped to the supplied tenant. Deleted users are excluded.
 */
@Service
public class IdentityOverviewService {

    private final OrganizationMemberRepository memberRepository;

    public IdentityOverviewService(OrganizationMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /** Learner totals split by account status; {@code total} counts every non-deleted student. */
    public record StudentStatusCounts(long total, long active, long suspended) {
    }

    /** Active, non-deleted exam staff of the tenant. */
    public record StaffCounts(long proctors, long examiners) {
    }

    @Transactional(readOnly = true)
    public StudentStatusCounts studentStatusCounts(UUID tenantId) {
        long active = 0L;
        long suspended = 0L;
        long total = 0L;
        List<Object[]> rows = memberRepository.countByOrganizationAndRoleGroupedByStatus(tenantId, Role.STUDENT);
        for (Object[] row : rows) {
            UserStatus status = (UserStatus) row[0];
            long count = ((Number) row[1]).longValue();
            total += count;
            if (status == UserStatus.ACTIVE) {
                active += count;
            } else if (status == UserStatus.SUSPENDED) {
                suspended += count;
            }
        }
        return new StudentStatusCounts(total, active, suspended);
    }

    /** Students created in {@code [from, to)}. */
    @Transactional(readOnly = true)
    public long countStudentsCreatedBetween(UUID tenantId, Instant from, Instant to) {
        return memberRepository.countByOrganizationAndRoleCreatedBetween(tenantId, Role.STUDENT, from, to);
    }

    /**
     * Platform dashboard read: non-deleted students per tenant public id, from one grouped query.
     * Tenants without students are absent from the map; callers join it with seat limits in memory.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Long> studentCountsByTenant() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : memberRepository.countByRoleGroupedByOrganization(Role.STUDENT)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return Collections.unmodifiableMap(counts);
    }

    @Transactional(readOnly = true)
    public StaffCounts activeStaffCounts(UUID tenantId) {
        long proctors = 0L;
        long examiners = 0L;
        List<Object[]> rows = memberRepository.countActiveByOrganizationAndRoles(
                tenantId, Set.of(Role.PROCTOR, Role.EXAMINER), UserStatus.ACTIVE);
        for (Object[] row : rows) {
            long count = ((Number) row[1]).longValue();
            if (row[0] == Role.PROCTOR) {
                proctors = count;
            } else if (row[0] == Role.EXAMINER) {
                examiners = count;
            }
        }
        return new StaffCounts(proctors, examiners);
    }
}
