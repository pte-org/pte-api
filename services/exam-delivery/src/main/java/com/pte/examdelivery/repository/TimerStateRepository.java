package com.pte.examdelivery.repository;

import com.pte.examdelivery.domain.TimerState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TimerStateRepository extends JpaRepository<TimerState, Long> {

    Optional<TimerState> findByAttemptId(Long attemptId);
}
