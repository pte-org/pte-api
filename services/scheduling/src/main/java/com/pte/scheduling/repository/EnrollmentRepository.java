package com.pte.scheduling.repository;

import com.pte.scheduling.domain.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findBySessionId(Long sessionId);

    boolean existsBySessionIdAndStudentPublicId(Long sessionId, UUID studentPublicId);

    Optional<Enrollment> findByPublicId(UUID publicId);

    List<Enrollment> findBySessionIdAndStudentPublicIdIn(Long sessionId, List<UUID> studentPublicIds);

    /**
     * Backs {@code admin}'s pending-exam-request transfer warning (join-fetches
     * {@code session} in the same query — a student's enrollment history is
     * short, but this still avoids one lazy load per row for consistency with
     * this repo's N+1-avoidance convention).
     */
    @Query("select e from Enrollment e join fetch e.session where e.studentPublicId = :studentPublicId and e.tenantId = :tenantId")
    List<Enrollment> findByStudentPublicIdAndTenantId(@Param("studentPublicId") UUID studentPublicId,
            @Param("tenantId") UUID tenantId);
}
