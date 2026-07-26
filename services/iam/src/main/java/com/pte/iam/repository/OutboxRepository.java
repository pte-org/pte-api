package com.pte.iam.repository;

import com.pte.iam.domain.OutboxEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {
}
