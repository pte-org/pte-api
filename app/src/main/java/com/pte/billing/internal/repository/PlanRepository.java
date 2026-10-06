package com.pte.billing.internal.repository;

import com.pte.billing.domain.Plan;
import com.pte.billing.domain.enums.PlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    Optional<Plan> findByPublicId(UUID publicId);

    List<Plan> findByPublicIdIn(Collection<UUID> publicIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Plan p where p.publicId = :publicId")
    Optional<Plan> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    @Query(value = """
            SELECT plan_id FROM orders WHERE plan_id IN (:ids)
            UNION SELECT plan_id FROM subscriptions WHERE plan_id IN (:ids)
            UNION SELECT plan_id FROM license_codes WHERE plan_id IN (:ids)
            """, nativeQuery = true)
    Set<UUID> findReferencedPlanIds(@Param("ids") Collection<UUID> ids);

    @Query(value = """
            SELECT DISTINCT plan_id FROM license_codes WHERE plan_id IN (:ids)
            AND status = 'ISSUED' AND (code_expires_at IS NULL OR code_expires_at > :now)
            """, nativeQuery = true)
    Set<UUID> findOutstandingCodePlanIds(@Param("ids") Collection<UUID> ids, @Param("now") Instant now);

    List<Plan> findAllByOrderByCreatedAtDesc();

    List<Plan> findByStatusOrderByCreatedAtDesc(PlanStatus status);
}
