package com.pte.session.internal.repository;

import com.pte.session.domain.ExamRegistration;
import com.pte.session.domain.enums.AudienceMemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Registrations of every status (the audience decision of a draft and the roster of a published session).
 * Roster, capacity, conflict and attendance reads select {@code ENROLLED} explicitly; the unfiltered finders
 * are for the audience workflow that owns the other statuses.
 */
public interface ExamRegistrationRepository extends JpaRepository<ExamRegistration, Long> {

    Optional<ExamRegistration> findByPublicId(UUID publicId);

    List<ExamRegistration> findBySessionIdOrderByStudentPublicIdAsc(Long sessionId);

    List<ExamRegistration> findBySessionIdAndStatus(Long sessionId, AudienceMemberStatus status);

    /** Backs the capacity checks: only enrolled students occupy a seat. */
    long countBySessionIdAndStatus(Long sessionId, AudienceMemberStatus status);

    boolean existsBySessionIdAndStudentPublicIdAndStatus(Long sessionId, UUID studentPublicId,
            AudienceMemberStatus status);

    /** Rows of any status for the given students, so a bulk enroll can promote an excluded or eligible row. */
    List<ExamRegistration> findBySessionIdAndStudentPublicIdIn(Long sessionId, List<UUID> studentPublicIds);

    void deleteBySessionId(Long sessionId);

    /** Removes the enrolled rows of the given students, e.g. when a class assigned to a scheduled exam is dropped. */
    long deleteBySessionIdAndStudentPublicIdInAndStatus(Long sessionId, List<UUID> studentPublicIds,
            AudienceMemberStatus status);

    /** Overview: enrolled learners per session for a small id set, grouped so no row per student is read. */
    @Query("select r.session.id, count(r) from ExamRegistration r "
            + "where r.session.id in :sessionIds and r.deleted = false "
            + "and r.status = com.pte.session.domain.enums.AudienceMemberStatus.ENROLLED group by r.session.id")
    List<Object[]> countEnrolledGroupedBySessionIds(@Param("sessionIds") List<Long> sessionIds);

    /** Finds tenant-wide slot conflicts in one query; cancelled sessions are not blockers. */
    @Query("select r.studentPublicId as studentPublicId, s.publicId as conflictingSessionPublicId, "
            + "s.name as conflictingSessionName from ExamRegistration r join r.session s "
            + "where r.tenantId = :tenantId and s.tenantId = :tenantId "
            + "and r.studentPublicId in :studentPublicIds and r.deleted = false and s.deleted = false "
            + "and r.status = com.pte.session.domain.enums.AudienceMemberStatus.ENROLLED "
            + "and s.status <> com.pte.session.domain.enums.SessionStatus.CANCELLED "
            + "and s.opensAt < :closesAt and s.closesAt > :opensAt "
            + "order by r.studentPublicId, s.opensAt, s.id")
    List<SlotConflictProjection> findConflictingEnrollments(@Param("tenantId") UUID tenantId,
            @Param("studentPublicIds") List<UUID> studentPublicIds,
            @Param("opensAt") Instant opensAt,
            @Param("closesAt") Instant closesAt);

    interface SlotConflictProjection {
        UUID getStudentPublicId();

        UUID getConflictingSessionPublicId();

        String getConflictingSessionName();
    }

    /** A student's enrollments, with the session join-fetched so mapping triggers no lazy load per row. */
    @Query("select r from ExamRegistration r join fetch r.session where r.studentPublicId = :studentPublicId "
            + "and r.tenantId = :tenantId "
            + "and r.status = com.pte.session.domain.enums.AudienceMemberStatus.ENROLLED")
    List<ExamRegistration> findEnrollmentsOfStudent(@Param("studentPublicId") UUID studentPublicId,
            @Param("tenantId") UUID tenantId);

    /** Batch read used by the audience rules: other sessions the students are enrolled in. */
    @Query("select r from ExamRegistration r join fetch r.session "
            + "where r.tenantId = :tenantId and r.studentPublicId in :studentPublicIds "
            + "and r.status = com.pte.session.domain.enums.AudienceMemberStatus.ENROLLED")
    List<ExamRegistration> findEnrollmentsOfStudents(@Param("tenantId") UUID tenantId,
            @Param("studentPublicIds") List<UUID> studentPublicIds);

    @Query("select r from ExamRegistration r "
            + "where r.session.publicId = :sessionPublicId and r.tenantId = :tenantId "
            + "and r.deleted = false and r.session.deleted = false "
            + "and r.status = com.pte.session.domain.enums.AudienceMemberStatus.ENROLLED")
    Page<ExamRegistration> findEnrolledBySessionPublicId(@Param("sessionPublicId") UUID sessionPublicId,
            @Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("select r.studentPublicId from ExamRegistration r "
            + "where r.session.publicId = :sessionPublicId and r.tenantId = :tenantId "
            + "and r.deleted = false and r.session.deleted = false "
            + "and r.status = com.pte.session.domain.enums.AudienceMemberStatus.ENROLLED")
    List<UUID> findEnrolledStudentPublicIds(@Param("sessionPublicId") UUID sessionPublicId,
            @Param("tenantId") UUID tenantId);

    @Query("select count(r) from ExamRegistration r "
            + "where r.session.publicId = :sessionPublicId and r.tenantId = :tenantId "
            + "and r.deleted = false and r.session.deleted = false "
            + "and r.status = com.pte.session.domain.enums.AudienceMemberStatus.ENROLLED")
    long countEnrolledBySessionPublicId(@Param("sessionPublicId") UUID sessionPublicId,
            @Param("tenantId") UUID tenantId);

    @Query("select case when count(r) > 0 then true else false end from ExamRegistration r "
            + "where r.session.publicId = :sessionPublicId and r.studentPublicId = :studentPublicId "
            + "and r.tenantId = :tenantId and r.deleted = false and r.session.deleted = false "
            + "and r.status = com.pte.session.domain.enums.AudienceMemberStatus.ENROLLED")
    boolean isStudentEnrolled(@Param("sessionPublicId") UUID sessionPublicId,
            @Param("studentPublicId") UUID studentPublicId, @Param("tenantId") UUID tenantId);

    @Query("select r from ExamRegistration r "
            + "where r.session.publicId = :sessionPublicId and r.studentPublicId = :studentPublicId "
            + "and r.tenantId = :tenantId and r.deleted = false and r.session.deleted = false "
            + "and r.status = com.pte.session.domain.enums.AudienceMemberStatus.ENROLLED")
    Optional<ExamRegistration> findActiveEnrollment(@Param("sessionPublicId") UUID sessionPublicId,
            @Param("studentPublicId") UUID studentPublicId, @Param("tenantId") UUID tenantId);
}
