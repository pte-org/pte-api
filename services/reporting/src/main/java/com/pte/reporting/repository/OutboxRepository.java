package com.pte.reporting.repository;

import com.pte.common.messaging.OutboxJpaRepository;
import com.pte.reporting.domain.OutboxEntry;

public interface OutboxRepository extends OutboxJpaRepository<OutboxEntry> {
}
