package com.pte.admin.repository;

import com.pte.admin.domain.LecturerAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LecturerAssignmentRepository extends JpaRepository<LecturerAssignment, Long> {

    Optional<LecturerAssignment> findByPublicId(UUID publicId);

    List<LecturerAssignment> findByStudentClass_PublicId(UUID classPublicId);
}
