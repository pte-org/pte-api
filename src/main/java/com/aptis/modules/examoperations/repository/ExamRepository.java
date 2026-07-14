package com.aptis.modules.examoperations.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aptis.modules.examoperations.domain.Exam;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findByHostIdAndIsAssignableTrue(String hostId);

    Optional<Exam> findByIdAndHostId(Long id, String hostId);
}
