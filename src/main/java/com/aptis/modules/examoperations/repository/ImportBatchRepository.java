package com.aptis.modules.examoperations.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aptis.modules.examoperations.domain.ImportBatch;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    Optional<ImportBatch> findByIdAndHostId(Long id, String hostId);

    List<ImportBatch> findByHostId(String hostId);
}
