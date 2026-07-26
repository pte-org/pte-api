package com.pte.proctor.repository;

import com.pte.proctor.domain.OutboxEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {
}
