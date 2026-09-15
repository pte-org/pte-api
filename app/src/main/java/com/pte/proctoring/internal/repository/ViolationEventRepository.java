package com.pte.proctoring.internal.repository;

import com.pte.proctoring.domain.ViolationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ViolationEventRepository extends JpaRepository<ViolationEvent, Long> {

    List<ViolationEvent> findBySessionPublicIdAndTenantIdOrderByDetectedAtAsc(UUID sessionPublicId, UUID tenantId);
}
