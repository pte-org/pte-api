package com.pte.reporting.internal.repository;

import com.pte.reporting.domain.AttemptReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Sort;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface AttemptReportRepository extends JpaRepository<AttemptReport, Long>,
        JpaSpecificationExecutor<AttemptReport> {

    Optional<AttemptReport> findByAttemptPublicId(UUID attemptPublicId);

    List<AttemptReport> findByStudentPublicIdAndTenantIdAndPublishedTrueAndDeletedFalseOrderByPublishedAtDesc(
            UUID studentPublicId, UUID tenantId);

    List<AttemptReport> findByTenantIdAndStudentPublicIdAndDeletedFalseAndAttemptPublicIdIn(
            UUID tenantId, UUID studentPublicId, List<UUID> attemptPublicIds);

    /**
     * Optional date predicates are assembled only when a bound exists. This avoids
     * PostgreSQL treating a nullable JPQL parameter in {@code :from is null} as
     * an untyped placeholder.
     */
    default List<AttemptReport> findPublishedImmutableForStudent(UUID tenantId, UUID studentPublicId,
            java.time.Instant from, java.time.Instant to) {
        return findAll(AttemptReportSpecifications.forStudent(tenantId, studentPublicId, from, to),
                Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id")));
    }

    java.util.Optional<AttemptReport> findFirstBySessionPublicIdAndTenantIdAndPublishedTrueAndReportSnapshotJsonIsNotNullOrderByPublishedAtAsc(
            UUID sessionPublicId, UUID tenantId);

    long countBySessionPublicIdAndTenantIdAndPublishedTrueAndReportSnapshotJsonIsNotNull(
            UUID sessionPublicId, UUID tenantId);
}
