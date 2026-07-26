package com.pte.examdelivery.repository;

import com.pte.examdelivery.domain.OutboxEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {
}
