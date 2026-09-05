package com.pte.scheduling.repository;

import com.pte.scheduling.domain.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findBySessionId(Long sessionId);

    boolean existsBySessionIdAndStudentPublicId(Long sessionId, UUID studentPublicId);

    Optional<Enrollment> findByPublicId(UUID publicId);

    List<Enrollment> findBySessionIdAndStudentPublicIdIn(Long sessionId, List<UUID> studentPublicIds);
}
