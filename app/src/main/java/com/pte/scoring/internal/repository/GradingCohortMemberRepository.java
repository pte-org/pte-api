package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.GradingCohortMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GradingCohortMemberRepository extends JpaRepository<GradingCohortMember, Long> {
    List<GradingCohortMember> findByCohortPublicIdAndDeletedFalse(UUID cohortPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from GradingCohortMember m where m.cohortPublicId = :cohortPublicId and m.deleted = false "
            + "order by m.id")
    List<GradingCohortMember> findForUpdate(@Param("cohortPublicId") UUID cohortPublicId);
}
