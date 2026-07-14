package com.aptis.modules.examoperations.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aptis.modules.examoperations.domain.CredentialExport;

public interface CredentialExportRepository extends JpaRepository<CredentialExport, Long> {
}
