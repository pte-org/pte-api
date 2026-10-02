package com.pte.session.internal.repository;

import com.pte.session.domain.ExamAudienceSource;
import com.pte.session.domain.enums.AudienceSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamAudienceSourceRepository extends JpaRepository<ExamAudienceSource, Long> {

    List<ExamAudienceSource> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    Optional<ExamAudienceSource> findByPublicIdAndSessionId(UUID publicId, Long sessionId);

    boolean existsBySessionIdAndSourceTypeAndSourcePublicId(Long sessionId, AudienceSourceType sourceType,
            UUID sourcePublicId);

    void deleteBySessionId(Long sessionId);
}
