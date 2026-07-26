package com.pte.proctor.repository;

import com.pte.proctor.domain.ViolationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ViolationEventRepository extends JpaRepository<ViolationEvent, Long> {

    List<ViolationEvent> findBySessionPublicIdAndTenantIdOrderByDetectedAtAsc(UUID sessionPublicId, UUID tenantId);
}
