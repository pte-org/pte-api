package com.pte.iam.repository;

import com.pte.common.messaging.OutboxJpaRepository;
import com.pte.iam.domain.OutboxEntry;

public interface OutboxRepository extends OutboxJpaRepository<OutboxEntry> {
}
