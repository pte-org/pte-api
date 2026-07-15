package com.aptis.modules.examoperations.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptis.modules.examoperations.domain.SessionTimeOverride;

public interface SessionTimeOverrideRepository extends JpaRepository<SessionTimeOverride, Long> {

    List<SessionTimeOverride> findByExamId(Long examId);

    @Modifying
    @Query("DELETE FROM SessionTimeOverride o WHERE o.exam.id = :examId")
    void deleteByExamId(@Param("examId") Long examId);
}
