package com.pte.examdelivery.repository;

import com.pte.examdelivery.domain.AttemptHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttemptHeartbeatRepository extends JpaRepository<AttemptHeartbeat, Long> {

    Optional<AttemptHeartbeat> findByAttemptId(Long attemptId);
}
