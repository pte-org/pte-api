package com.pte.examdelivery.repository;

import com.pte.examdelivery.domain.TimerState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TimerStateRepository extends JpaRepository<TimerState, Long> {

    Optional<TimerState> findByAttemptId(Long attemptId);

    /** Serializes concurrent audio-play requests for the same attempt (Phase 6 concurrency requirement). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TimerState t WHERE t.attempt.id = :attemptId")
    Optional<TimerState> findWithLockByAttemptId(@Param("attemptId") Long attemptId);
}
