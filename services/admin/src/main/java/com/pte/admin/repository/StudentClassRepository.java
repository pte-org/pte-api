package com.pte.admin.repository;

import com.pte.admin.domain.StudentClass;
import com.pte.admin.dto.response.ClassStudentCountResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentClassRepository extends JpaRepository<StudentClass, Long> {

    Optional<StudentClass> findByPublicId(UUID publicId);

    List<StudentClass> findByProgram_PublicIdAndDeletedFalseOrderByCreatedAtAsc(UUID programPublicId);

    boolean existsByProgram_PublicIdAndNameIgnoreCaseAndDeletedFalse(UUID programPublicId, String name);

    /** Used by {@code ProgramService.archive()}'s active-children guard. */
    boolean existsByProgram_PublicIdAndDeletedFalse(UUID programPublicId);

    /**
     * Backs Phase 13's Program dashboard — one grouped {@code LEFT JOIN}
     * query returns every non-archived Class under a Program together with
     * its live student count (0 for a Class with no members yet), so the
     * dashboard never issues a second per-Class query (N+1 discipline,
     * matching {@code ClassMembershipRepository}'s existing
     * {@code @EntityGraph} queries).
     */
    @Query("SELECT new com.pte.admin.dto.response.ClassStudentCountResponse(c.publicId, c.name, COUNT(m)) "
            + "FROM StudentClass c LEFT JOIN ClassMembership m ON m.studentClass = c "
            + "WHERE c.program.publicId = :programPublicId AND c.deleted = false "
            + "GROUP BY c.publicId, c.name "
            + "ORDER BY c.name ASC")
    List<ClassStudentCountResponse> countStudentsByClassForProgram(@Param("programPublicId") UUID programPublicId);
}
