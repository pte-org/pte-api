package com.aptis.modules.iam.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptis.modules.iam.domain.Student;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByOrganizationId(Long organizationId);

    Page<Student> findByOrganizationIdAndDeletedFalse(Long organizationId, Pageable pageable);

    @Query("SELECT s.username FROM Student s WHERE s.username IN :usernames")
    Set<String> findExistingUsernames(@Param("usernames") Set<String> usernames);

    @Query("""
            SELECT s.email FROM Student s
            WHERE s.email IN :emails
            AND s.organizationId = :organizationId
            AND s.email IS NOT NULL
            """)
    Set<String> findExistingEmails(
            @Param("emails") Set<String> emails,
            @Param("organizationId") Long organizationId);
}
