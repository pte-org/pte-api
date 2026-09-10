package com.pte.admin.repository;

import com.pte.admin.domain.ProgramCoordinatorAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgramCoordinatorAssignmentRepository extends JpaRepository<ProgramCoordinatorAssignment, Long> {

    Optional<ProgramCoordinatorAssignment> findByPublicId(UUID publicId);

    List<ProgramCoordinatorAssignment> findByProgram_PublicId(UUID programPublicId);
}
