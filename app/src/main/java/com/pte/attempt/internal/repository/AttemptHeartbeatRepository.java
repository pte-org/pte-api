package com.pte.attempt.internal.repository;

import com.pte.attempt.domain.AttemptHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttemptHeartbeatRepository extends JpaRepository<AttemptHeartbeat, Long> {

    Optional<AttemptHeartbeat> findByAttemptId(Long attemptId);

    @Query("SELECT h FROM AttemptHeartbeat h WHERE h.attempt.id IN :attemptIds")
    List<AttemptHeartbeat> findByAttemptIdIn(@Param("attemptIds") Collection<Long> attemptIds);
}
