package com.pte.billing.internal.repository;

import com.pte.billing.domain.Plan;
import com.pte.billing.domain.enums.PlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    Optional<Plan> findByPublicId(UUID publicId);

    List<Plan> findAllByOrderByCreatedAtDesc();

    List<Plan> findByStatusOrderByCreatedAtDesc(PlanStatus status);
}
