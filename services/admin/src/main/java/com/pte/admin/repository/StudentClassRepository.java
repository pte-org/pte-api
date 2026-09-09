package com.pte.admin.repository;

import com.pte.admin.domain.StudentClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentClassRepository extends JpaRepository<StudentClass, Long> {

    Optional<StudentClass> findByPublicId(UUID publicId);

    List<StudentClass> findByProgram_PublicIdAndDeletedFalseOrderByCreatedAtAsc(UUID programPublicId);

    boolean existsByProgram_PublicIdAndNameIgnoreCaseAndDeletedFalse(UUID programPublicId, String name);

    /** Used by {@code ProgramService.archive()}'s active-children guard. */
    boolean existsByProgram_PublicIdAndDeletedFalse(UUID programPublicId);
}
