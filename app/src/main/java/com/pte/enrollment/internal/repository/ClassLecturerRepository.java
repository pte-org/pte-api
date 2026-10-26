package com.pte.enrollment.internal.repository;

import com.pte.enrollment.domain.ClassLecturer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassLecturerRepository extends JpaRepository<ClassLecturer, Long> {

    Optional<ClassLecturer> findByPublicIdAndOrganizationId(UUID publicId, UUID tenantId);

    List<ClassLecturer> findByOrganizationIdAndTrainingClass_PublicId(UUID tenantId, UUID classPublicId);
}
