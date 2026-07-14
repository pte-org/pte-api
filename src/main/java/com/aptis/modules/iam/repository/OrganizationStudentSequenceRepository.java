package com.aptis.modules.iam.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptis.modules.iam.domain.OrganizationStudentSequence;

import jakarta.persistence.LockModeType;

public interface OrganizationStudentSequenceRepository
        extends JpaRepository<OrganizationStudentSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT sequence FROM OrganizationStudentSequence sequence
            WHERE sequence.organizationId = :organizationId
            """)
    Optional<OrganizationStudentSequence> findByOrganizationIdForUpdate(
            @Param("organizationId") Long organizationId);
}
