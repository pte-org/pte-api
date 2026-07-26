package com.pte.authoring.repository;

import com.pte.authoring.domain.OutboxEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {
}
