package com.pte.scoring.repository;

import com.pte.common.messaging.OutboxJpaRepository;
import com.pte.scoring.domain.OutboxEntry;

public interface OutboxRepository extends OutboxJpaRepository<OutboxEntry> {
}
