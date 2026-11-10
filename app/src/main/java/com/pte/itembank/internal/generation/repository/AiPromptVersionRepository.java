package com.pte.itembank.internal.generation.repository;

import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.domain.AiPromptStatus;
import com.pte.itembank.internal.generation.domain.AiPromptVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiPromptVersionRepository extends JpaRepository<AiPromptVersion, Long> {

    Optional<AiPromptVersion> findByPublicId(UUID publicId);

    Optional<AiPromptVersion> findByKindAndPromptKeyAndStatus(AiPromptKind kind, String promptKey,
                                                              AiPromptStatus status);

    List<AiPromptVersion> findByKindAndStatus(AiPromptKind kind, AiPromptStatus status);

    List<AiPromptVersion> findByKindAndPromptKeyOrderByVersionNoDesc(AiPromptKind kind, String promptKey);

    boolean existsByKindAndPromptKey(AiPromptKind kind, String promptKey);

    /** Native on purpose: a discarded draft is hidden from entity queries but still owns its version number. */
    @Query(value = "select coalesce(max(version_no), 0) from ai_prompt_versions "
            + "where kind = :kind and prompt_key = :promptKey", nativeQuery = true)
    int highestVersionNo(@Param("kind") String kind, @Param("promptKey") String promptKey);

    /** Retires the live version of a prompt before another one is activated, so the one-live index never trips. */
    @Modifying(flushAutomatically = true)
    @Query("update AiPromptVersion v set v.status = com.pte.itembank.internal.generation.domain.AiPromptStatus.RETIRED, "
            + "v.retiredAt = :now where v.kind = :kind and v.promptKey = :promptKey "
            + "and v.status = com.pte.itembank.internal.generation.domain.AiPromptStatus.ACTIVE")
    int retireActive(@Param("kind") AiPromptKind kind, @Param("promptKey") String promptKey,
                     @Param("now") Instant now);
}
