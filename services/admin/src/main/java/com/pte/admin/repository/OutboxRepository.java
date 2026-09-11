package com.pte.admin.repository;

import com.pte.admin.domain.OutboxEntry;
import com.pte.common.messaging.OutboxJpaRepository;

public interface OutboxRepository extends OutboxJpaRepository<OutboxEntry> {
}
