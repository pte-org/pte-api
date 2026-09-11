package com.pte.authoring.repository;

import com.pte.authoring.domain.OutboxEntry;
import com.pte.common.messaging.OutboxJpaRepository;

public interface OutboxRepository extends OutboxJpaRepository<OutboxEntry> {
}
