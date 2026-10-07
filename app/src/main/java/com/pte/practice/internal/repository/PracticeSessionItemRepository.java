package com.pte.practice.internal.repository;

import com.pte.practice.internal.domain.PracticeSessionItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeSessionItemRepository extends JpaRepository<PracticeSessionItem, Long> {

    List<PracticeSessionItem> findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(Long practiceSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select item from PracticeSessionItem item
             where item.publicId = :publicId
               and item.practiceSessionId = :practiceSessionId
               and item.deleted = false
            """)
    Optional<PracticeSessionItem> findWithLockByPublicIdAndPracticeSessionId(
            @Param("publicId") UUID publicId, @Param("practiceSessionId") Long practiceSessionId);
}
