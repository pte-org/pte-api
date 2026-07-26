package com.pte.reporting.repository;

import com.pte.reporting.domain.OutboxEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {
}
