package com.pte.admin.repository;

import com.pte.admin.domain.StudentRosterEntry;
import com.pte.admin.dto.response.StudentRosterRowResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface StudentRosterEntryRepository extends JpaRepository<StudentRosterEntry, Long> {

    Optional<StudentRosterEntry> findByTenantIdAndStudentPublicId(UUID tenantId, UUID studentPublicId);

    /**
     * Server-side tenant roster query. ClassMembership is unique by student,
     * so the left join does not fan out the page or count.
     */
    @Query(value = """
            SELECT new com.pte.admin.dto.response.StudentRosterRowResponse(
                entry.studentPublicId, entry.email, entry.fullName, entry.studentCode,
                entry.phone, entry.status, entry.createdAt,
                program.publicId, program.name, studentClass.publicId, studentClass.name)
            FROM StudentRosterEntry entry
            LEFT JOIN ClassMembership membership
                ON membership.studentPublicId = entry.studentPublicId
                AND membership.tenantId = :tenantId
            LEFT JOIN membership.studentClass studentClass
            LEFT JOIN studentClass.program program
            WHERE entry.tenantId = :tenantId
              AND (:search = ''
                   OR LOWER(entry.fullName) LIKE CONCAT('%', :search, '%')
                   OR LOWER(entry.email) LIKE CONCAT('%', :search, '%')
                   OR LOWER(COALESCE(entry.phone, '')) LIKE CONCAT('%', :search, '%')
                   OR LOWER(COALESCE(entry.studentCode, '')) LIKE CONCAT('%', :search, '%'))
              AND (:programPublicId IS NULL OR program.publicId = :programPublicId)
              AND (:classPublicId IS NULL OR studentClass.publicId = :classPublicId)
              AND (:assignmentStatus = 'ALL'
                   OR (:assignmentStatus = 'ASSIGNED' AND membership.id IS NOT NULL)
                   OR (:assignmentStatus = 'UNASSIGNED' AND membership.id IS NULL))
            ORDER BY
                CASE WHEN :sort = 'CREATED_AT' AND :direction = 'ASC' THEN entry.createdAt END ASC,
                CASE WHEN :sort = 'CREATED_AT' AND :direction = 'DESC' THEN entry.createdAt END DESC,
                CASE WHEN :sort = 'FULL_NAME' AND :direction = 'ASC' THEN LOWER(entry.fullName) END ASC,
                CASE WHEN :sort = 'FULL_NAME' AND :direction = 'DESC' THEN LOWER(entry.fullName) END DESC,
                CASE WHEN :sort = 'STUDENT_CODE' AND :direction = 'ASC' THEN LOWER(COALESCE(entry.studentCode, '')) END ASC,
                CASE WHEN :sort = 'STUDENT_CODE' AND :direction = 'DESC' THEN LOWER(COALESCE(entry.studentCode, '')) END DESC,
                CASE WHEN :direction = 'ASC' THEN entry.studentPublicId END ASC,
                CASE WHEN :direction = 'DESC' THEN entry.studentPublicId END DESC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT entry.id)
            FROM StudentRosterEntry entry
            LEFT JOIN ClassMembership membership
                ON membership.studentPublicId = entry.studentPublicId
                AND membership.tenantId = :tenantId
            LEFT JOIN membership.studentClass studentClass
            LEFT JOIN studentClass.program program
            WHERE entry.tenantId = :tenantId
              AND (:search = ''
                   OR LOWER(entry.fullName) LIKE CONCAT('%', :search, '%')
                   OR LOWER(entry.email) LIKE CONCAT('%', :search, '%')
                   OR LOWER(COALESCE(entry.phone, '')) LIKE CONCAT('%', :search, '%')
                   OR LOWER(COALESCE(entry.studentCode, '')) LIKE CONCAT('%', :search, '%'))
              AND (:programPublicId IS NULL OR program.publicId = :programPublicId)
              AND (:classPublicId IS NULL OR studentClass.publicId = :classPublicId)
              AND (:assignmentStatus = 'ALL'
                   OR (:assignmentStatus = 'ASSIGNED' AND membership.id IS NOT NULL)
                   OR (:assignmentStatus = 'UNASSIGNED' AND membership.id IS NULL))
            """)
    Page<StudentRosterRowResponse> findPageForTenant(@Param("tenantId") UUID tenantId,
            @Param("search") String search,
            @Param("programPublicId") UUID programPublicId,
            @Param("classPublicId") UUID classPublicId,
            @Param("assignmentStatus") String assignmentStatus,
            @Param("sort") String sort,
            @Param("direction") String direction,
            Pageable pageable);

    /** Atomic projection upsert: safe when a rebuild and the live consumer race. */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO student_roster_entries
                (student_public_id, tenant_id, email, full_name, student_code, phone, status, created_at)
            VALUES
                (:studentPublicId, :tenantId, :email, :fullName, :studentCode, :phone, :status, :createdAt)
            ON CONFLICT (tenant_id, student_public_id) DO UPDATE SET
                email = EXCLUDED.email,
                full_name = EXCLUDED.full_name,
                student_code = EXCLUDED.student_code,
                phone = EXCLUDED.phone,
                status = EXCLUDED.status,
                created_at = EXCLUDED.created_at
            """, nativeQuery = true)
    int upsert(@Param("studentPublicId") UUID studentPublicId,
            @Param("tenantId") UUID tenantId,
            @Param("email") String email,
            @Param("fullName") String fullName,
            @Param("studentCode") String studentCode,
            @Param("phone") String phone,
            @Param("status") String status,
            @Param("createdAt") java.time.Instant createdAt);
}
