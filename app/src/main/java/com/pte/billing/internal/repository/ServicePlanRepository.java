package com.pte.billing.internal.repository;

import com.pte.billing.domain.ServicePlan;
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

public interface ServicePlanRepository extends JpaRepository<ServicePlan, Long> {

    Optional<ServicePlan> findByPublicId(UUID publicId);

    List<ServicePlan> findByPublicIdIn(Collection<UUID> publicIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ServicePlan p where p.publicId = :publicId")
    Optional<ServicePlan> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

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

    /** Overview: non-deleted plans grouped by lifecycle status. */
    @Query("select p.status, count(p) from ServicePlan p where p.deleted = false group by p.status")
    List<Object[]> countGroupedByStatus();

    /** Overview: non-deleted plans grouped by plan type. */
    @Query("select p.type, count(p) from ServicePlan p where p.deleted = false group by p.type")
    List<Object[]> countGroupedByType();

    List<ServicePlan> findAllByOrderByCreatedAtDesc();

    List<ServicePlan> findByStatusOrderByCreatedAtDesc(PlanStatus status);
}
