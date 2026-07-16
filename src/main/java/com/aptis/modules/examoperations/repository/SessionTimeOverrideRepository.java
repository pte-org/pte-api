package com.aptis.modules.examoperations.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptis.modules.examoperations.domain.SessionTimeOverride;
import com.aptis.modules.questionbank.domain.Skill;

public interface SessionTimeOverrideRepository extends JpaRepository<SessionTimeOverride, Long> {

    List<SessionTimeOverride> findByExamId(Long examId);

    Optional<SessionTimeOverride> findByExamIdAndSkillAndPart(Long examId, Skill skill, Integer part);

    @Modifying
    @Query("DELETE FROM SessionTimeOverride o WHERE o.exam.id = :examId")
    void deleteByExamId(@Param("examId") Long examId);
}
