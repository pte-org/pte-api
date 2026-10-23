package com.pte.rubric.internal.repository;

import com.pte.rubric.domain.RubricRevision;
import com.pte.rubric.domain.enums.RubricStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RubricRevisionRepository extends JpaRepository<RubricRevision, Long> {

    @Query("SELECT DISTINCT r FROM RubricRevision r LEFT JOIN FETCH r.criteria c WHERE r.publicId = :publicId AND r.deleted = false")
    Optional<RubricRevision> findWithDetailsByPublicId(@Param("publicId") UUID publicId);

    @Query("SELECT DISTINCT r FROM RubricRevision r LEFT JOIN FETCH r.criteria c WHERE upper(r.taskTypeKey) = :taskTypeKey AND r.channel = :channel AND r.status = :status AND r.deleted = false")
    Optional<RubricRevision> findWithDetailsByTaskTypeKeyAndChannelAndStatus(
            @Param("taskTypeKey") String taskTypeKey,
            @Param("channel") com.pte.rubric.domain.enums.RubricChannel channel,
            @Param("status") RubricStatus status);

    Page<RubricRevision> findAllByDeletedFalseOrderByRubricKeyAscVersionDesc(Pageable pageable);

    @Query("SELECT r FROM RubricRevision r WHERE r.deleted = false AND (:status IS NULL OR r.status = :status) AND (:taskTypeKey IS NULL OR upper(r.taskTypeKey) = :taskTypeKey) AND (:rubricKey IS NULL OR upper(r.rubricKey) LIKE :rubricKey) AND (:authorUserPublicId IS NULL OR r.authorUserPublicId = :authorUserPublicId) AND (:version IS NULL OR r.version = :version) AND (:staffOnly = false OR r.status NOT IN (com.pte.rubric.domain.enums.RubricStatus.DRAFT, com.pte.rubric.domain.enums.RubricStatus.PENDING_APPROVAL) OR r.authorUserPublicId = :staffAuthorUserPublicId) ORDER BY r.rubricKey ASC, r.version DESC")
    Page<RubricRevision> findPage(@Param("status") RubricStatus status,
            @Param("taskTypeKey") String taskTypeKey,
            @Param("rubricKey") String rubricKey,
            @Param("authorUserPublicId") UUID authorUserPublicId,
            @Param("version") Integer version,
            @Param("staffOnly") boolean staffOnly,
            @Param("staffAuthorUserPublicId") UUID staffAuthorUserPublicId,
            Pageable pageable);

    @Query("SELECT COALESCE(MAX(r.version), 0) FROM RubricRevision r WHERE r.rubricKey = :rubricKey")
    int findMaxVersionByRubricKey(@Param("rubricKey") String rubricKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RubricRevision r WHERE r.rubricKey = :rubricKey AND r.deleted = false")
    List<RubricRevision> findAllByRubricKeyForUpdate(@Param("rubricKey") String rubricKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RubricRevision r WHERE r.taskTypeKey = :taskTypeKey AND r.channel = :channel AND r.status = :status AND r.deleted = false")
    Optional<RubricRevision> findByTaskTypeKeyAndChannelAndStatusForUpdate(
            @Param("taskTypeKey") String taskTypeKey,
            @Param("channel") com.pte.rubric.domain.enums.RubricChannel channel,
            @Param("status") RubricStatus status);
}
