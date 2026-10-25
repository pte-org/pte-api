package com.pte.session.internal.repository;

import com.pte.session.domain.ExamSessionAudienceSource;
import com.pte.session.domain.enums.AudienceSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamSessionAudienceSourceRepository extends JpaRepository<ExamSessionAudienceSource, Long> {

    List<ExamSessionAudienceSource> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    Optional<ExamSessionAudienceSource> findByPublicIdAndSessionId(UUID publicId, Long sessionId);

    boolean existsBySessionIdAndSourceTypeAndSourcePublicId(Long sessionId, AudienceSourceType sourceType,
            UUID sourcePublicId);

    Optional<ExamSessionAudienceSource> findBySessionIdAndSourceTypeAndSourcePublicId(Long sessionId,
            AudienceSourceType sourceType, UUID sourcePublicId);

    List<ExamSessionAudienceSource> findBySessionIdAndSourceTypeOrderByCreatedAtAsc(Long sessionId,
            AudienceSourceType sourceType);

    void deleteBySessionId(Long sessionId);
}
