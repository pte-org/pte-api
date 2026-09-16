package com.pte.attempt.internal.repository;

import com.pte.attempt.domain.AttemptHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttemptHeartbeatRepository extends JpaRepository<AttemptHeartbeat, Long> {

    Optional<AttemptHeartbeat> findByAttemptId(Long attemptId);
}
