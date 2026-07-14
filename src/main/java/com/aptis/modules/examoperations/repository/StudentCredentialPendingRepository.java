package com.aptis.modules.examoperations.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aptis.modules.examoperations.domain.StudentCredentialPending;

public interface StudentCredentialPendingRepository
        extends JpaRepository<StudentCredentialPending, Long> {

    List<StudentCredentialPending> findByImportBatchId(Long importBatchId);

    void deleteByImportBatchId(Long importBatchId);
}
