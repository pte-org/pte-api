package com.pte.enrollment.internal.repository;

import com.pte.enrollment.domain.StudentClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/** The enrollment-side read port for the identity/enrollment roster join. */
public interface StudentRosterRepository extends Repository<StudentClass, Long> {

    @Query(value = """
            SELECT CAST(u.public_id AS VARCHAR) AS studentPublicId,
                   u.email AS email,
                   u.full_name AS fullName,
                   u.student_code AS studentCode,
                   u.phone AS phone,
                   u.status AS status,
                   u.created_at AS createdAt,
                   CAST(p.public_id AS VARCHAR) AS programPublicId,
                   p.name AS programName,
                   CAST(c.public_id AS VARCHAR) AS classPublicId,
                   c.name AS className,
                   u.username AS username,
                   u.must_change_password AS mustChangePassword
            FROM identity_student_directory u
            LEFT JOIN class_memberships m
                   ON m.student_public_id = u.public_id
                  AND m.tenant_id = :tenantId
            LEFT JOIN student_classes c
                   ON c.id = m.student_class_id
                  AND EXISTS (
                      SELECT 1
                      FROM programs c_programs
                      JOIN organizations c_organizations ON c_organizations.id = c_programs.organization_id
                      JOIN tenants c_tenants ON c_tenants.id = c_organizations.tenant_id
                      WHERE c_programs.id = c.program_id
                        AND c_tenants.public_id = :tenantId)
            LEFT JOIN programs p
                   ON p.id = c.program_id
                  AND EXISTS (
                      SELECT 1
                      FROM organizations p_organizations
                      JOIN tenants p_tenants ON p_tenants.id = p_organizations.tenant_id
                      WHERE p_organizations.id = p.organization_id
                        AND p_tenants.public_id = :tenantId)
            WHERE u.tenant_id = :tenantId
              AND u.deleted = FALSE
              AND (:search = ''
                   OR LOWER(u.full_name) LIKE CONCAT('%', :search, '%')
                   OR LOWER(u.email) LIKE CONCAT('%', :search, '%')
                   OR LOWER(COALESCE(u.phone, '')) LIKE CONCAT('%', :search, '%')
                   OR LOWER(COALESCE(u.student_code, '')) LIKE CONCAT('%', :search, '%'))
              AND (:programPublicId IS NULL OR p.public_id = :programPublicId)
              AND (:classPublicId IS NULL OR c.public_id = :classPublicId)
              AND (:assignmentStatus = 'ALL'
                   OR (:assignmentStatus = 'ASSIGNED' AND m.id IS NOT NULL)
                   OR (:assignmentStatus = 'UNASSIGNED' AND m.id IS NULL))
            ORDER BY
                CASE WHEN :sort = 'CREATED_AT' AND :direction = 'ASC' THEN u.created_at END ASC,
                CASE WHEN :sort = 'CREATED_AT' AND :direction = 'DESC' THEN u.created_at END DESC,
                CASE WHEN :sort = 'FULL_NAME' AND :direction = 'ASC' THEN LOWER(u.full_name) END ASC,
                CASE WHEN :sort = 'FULL_NAME' AND :direction = 'DESC' THEN LOWER(u.full_name) END DESC,
                CASE WHEN :sort = 'STUDENT_CODE' AND :direction = 'ASC' THEN LOWER(COALESCE(u.student_code, '')) END ASC,
                CASE WHEN :sort = 'STUDENT_CODE' AND :direction = 'DESC' THEN LOWER(COALESCE(u.student_code, '')) END DESC,
                CASE WHEN :direction = 'ASC' THEN u.public_id END ASC,
                CASE WHEN :direction = 'DESC' THEN u.public_id END DESC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT u.id)
            FROM identity_student_directory u
            LEFT JOIN class_memberships m
                   ON m.student_public_id = u.public_id
                  AND m.tenant_id = :tenantId
            LEFT JOIN student_classes c
                   ON c.id = m.student_class_id
                  AND EXISTS (
                      SELECT 1
                      FROM programs c_programs
                      JOIN organizations c_organizations ON c_organizations.id = c_programs.organization_id
                      JOIN tenants c_tenants ON c_tenants.id = c_organizations.tenant_id
                      WHERE c_programs.id = c.program_id
                        AND c_tenants.public_id = :tenantId)
            LEFT JOIN programs p
                   ON p.id = c.program_id
                  AND EXISTS (
                      SELECT 1
                      FROM organizations p_organizations
                      JOIN tenants p_tenants ON p_tenants.id = p_organizations.tenant_id
                      WHERE p_organizations.id = p.organization_id
                        AND p_tenants.public_id = :tenantId)
            WHERE u.tenant_id = :tenantId
              AND u.deleted = FALSE
              AND (:search = ''
                   OR LOWER(u.full_name) LIKE CONCAT('%', :search, '%')
                   OR LOWER(u.email) LIKE CONCAT('%', :search, '%')
                   OR LOWER(COALESCE(u.phone, '')) LIKE CONCAT('%', :search, '%')
                   OR LOWER(COALESCE(u.student_code, '')) LIKE CONCAT('%', :search, '%'))
              AND (:programPublicId IS NULL OR p.public_id = :programPublicId)
              AND (:classPublicId IS NULL OR c.public_id = :classPublicId)
              AND (:assignmentStatus = 'ALL'
                   OR (:assignmentStatus = 'ASSIGNED' AND m.id IS NOT NULL)
                   OR (:assignmentStatus = 'UNASSIGNED' AND m.id IS NULL))
            """, nativeQuery = true)
    Page<StudentRosterRow> findPageForTenant(
            @Param("tenantId") UUID tenantId,
            @Param("search") String search,
            @Param("programPublicId") UUID programPublicId,
            @Param("classPublicId") UUID classPublicId,
            @Param("assignmentStatus") String assignmentStatus,
            @Param("sort") String sort,
            @Param("direction") String direction,
            Pageable pageable);
}
