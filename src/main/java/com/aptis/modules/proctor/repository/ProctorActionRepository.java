package com.aptis.modules.proctor.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aptis.modules.proctor.domain.ProctorAction;

/** Insert-only by contract: no update/delete method is ever added to this interface. */
public interface ProctorActionRepository extends JpaRepository<ProctorAction, Long> {
    List<ProctorAction> findByTargetAttemptId(Long targetAttemptId);
}
