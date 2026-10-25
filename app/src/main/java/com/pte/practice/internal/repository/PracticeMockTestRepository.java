package com.pte.practice.internal.repository;

import com.pte.practice.internal.domain.PracticeMockTest;
import com.pte.practice.internal.domain.enums.PracticeMockTestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeMockTestRepository extends JpaRepository<PracticeMockTest, Long> {

    List<PracticeMockTest> findByStatusAndDeletedFalseOrderBySequenceNoAsc(PracticeMockTestStatus status);

    List<PracticeMockTest> findByDeletedFalseOrderBySequenceNoAsc();

    Optional<PracticeMockTest> findByPublicIdAndDeletedFalse(UUID publicId);

    long countByDeletedFalse();

    @Query("select coalesce(max(test.sequenceNo), 0) from PracticeMockTest test")
    int findMaxSequenceNo();
}
