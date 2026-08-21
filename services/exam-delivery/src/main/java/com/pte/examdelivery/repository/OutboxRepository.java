package com.pte.examdelivery.repository;

import com.pte.common.messaging.OutboxJpaRepository;
import com.pte.examdelivery.domain.OutboxEntry;

public interface OutboxRepository extends OutboxJpaRepository<OutboxEntry> {
}
