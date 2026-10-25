package com.pte.reporting.internal.repository;

import com.pte.reporting.domain.ExamResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Sort;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ExamResultRepository extends JpaRepository<ExamResult, Long>,
        JpaSpecificationExecutor<ExamResult> {

    Optional<ExamResult> findByAttemptPublicId(UUID attemptPublicId);

    /** Overview: reports of the tenant that exist but are not yet published (count only). */
    long countByTenantIdAndPublishedFalseAndDeletedFalse(UUID tenantId);

    List<ExamResult> findByStudentPublicIdAndTenantIdAndPublishedTrueAndDeletedFalseOrderByPublishedAtDesc(
            UUID studentPublicId, UUID tenantId);

    List<ExamResult> findByTenantIdAndStudentPublicIdAndDeletedFalseAndAttemptPublicIdIn(
            UUID tenantId, UUID studentPublicId, List<UUID> attemptPublicIds);

    /**
     * Optional date predicates are assembled only when a bound exists. This avoids
     * PostgreSQL treating a nullable JPQL parameter in {@code :from is null} as
     * an untyped placeholder.
     */
    default List<ExamResult> findPublishedImmutableForStudent(UUID tenantId, UUID studentPublicId,
            java.time.Instant from, java.time.Instant to) {
        return findAll(ExamResultSpecifications.forStudent(tenantId, studentPublicId, from, to),
                Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id")));
    }

    java.util.Optional<ExamResult> findFirstBySessionPublicIdAndTenantIdAndPublishedTrueAndReportSnapshotJsonIsNotNullOrderByPublishedAtAsc(
            UUID sessionPublicId, UUID tenantId);

    long countBySessionPublicIdAndTenantIdAndPublishedTrueAndReportSnapshotJsonIsNotNull(
            UUID sessionPublicId, UUID tenantId);
}
