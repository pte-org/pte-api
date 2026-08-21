package com.pte.proctor.repository;

import com.pte.proctor.domain.OutboxEntry;
import com.pte.common.messaging.OutboxJpaRepository;

public interface OutboxRepository extends OutboxJpaRepository<OutboxEntry> {
}
