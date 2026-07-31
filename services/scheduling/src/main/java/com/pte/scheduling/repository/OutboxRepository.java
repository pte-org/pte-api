package com.pte.scheduling.repository;

import com.pte.common.messaging.OutboxJpaRepository;
import com.pte.scheduling.domain.OutboxEntry;

public interface OutboxRepository extends OutboxJpaRepository<OutboxEntry> {
}
