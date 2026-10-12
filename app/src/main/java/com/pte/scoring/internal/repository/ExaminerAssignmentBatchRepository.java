package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ExaminerAssignmentBatch;
import com.pte.scoring.domain.enums.AssignmentBatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExaminerAssignmentBatchRepository extends JpaRepository<ExaminerAssignmentBatch, Long> {

    Optional<ExaminerAssignmentBatch> findByPublicIdAndTenantIdAndSessionPublicId(
            UUID publicId, UUID tenantId, UUID sessionPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ExaminerAssignmentBatch b where b.publicId = :publicId "
            + "and b.tenantId = :tenantId and b.sessionPublicId = :sessionPublicId and b.deleted = false")
    Optional<ExaminerAssignmentBatch> findForUpdate(@Param("publicId") UUID publicId,
            @Param("tenantId") UUID tenantId, @Param("sessionPublicId") UUID sessionPublicId);

    Page<ExaminerAssignmentBatch> findByTenantIdAndSessionPublicIdAndDeletedFalse(UUID tenantId,
            UUID sessionPublicId, Pageable pageable);

    @Query("""
            select b from ExaminerAssignmentBatch b
             where b.tenantId = :tenantId
               and b.sessionPublicId = :sessionPublicId
               and b.deleted = false
               and (:search is null
                    or (:searchId is not null and (b.publicId = :searchId or b.createdByPublicId = :searchId))
                    or (:searchStatus is not null and b.status = :searchStatus)
                    or (:searchMode is not null and b.mode = :searchMode))
             order by b.createdAt desc, b.id desc
            """)
    Page<ExaminerAssignmentBatch> findForOverview(@Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId, @Param("search") String search,
            @Param("searchId") UUID searchId, @Param("searchStatus") AssignmentBatchStatus searchStatus,
            @Param("searchMode") com.pte.scoring.domain.enums.AssignmentBatchMode searchMode,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ExaminerAssignmentBatch b set b.status = :expired, b.lockVersion = b.lockVersion + 1 "
            + "where b.tenantId = :tenantId and b.sessionPublicId = :sessionPublicId and b.deleted = false "
            + "and b.status = :previewed and b.previewExpiresAt <= :now")
    int expireOverduePreviews(@Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId,
            @Param("previewed") AssignmentBatchStatus previewed,
            @Param("expired") AssignmentBatchStatus expired,
            @Param("now") Instant now);
}
