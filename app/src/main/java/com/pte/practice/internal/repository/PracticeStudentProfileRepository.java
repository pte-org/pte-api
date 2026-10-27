package com.pte.practice.internal.repository;

import com.pte.practice.internal.domain.PracticeStudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PracticeStudentProfileRepository extends JpaRepository<PracticeStudentProfile, Long> {

    Optional<PracticeStudentProfile> findByStudentPublicIdAndDeletedFalse(UUID studentPublicId);
}
