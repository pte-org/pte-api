package com.pte.assessment.internal.repository;

import com.pte.assessment.domain.ExamVersion;
import com.pte.assessment.domain.ExamVersionItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamVersionRepository extends JpaRepository<ExamVersion, Long> {

    Optional<ExamVersion> findByPublicId(UUID publicId);

    @EntityGraph(attributePaths = "items")
    Optional<ExamVersion> findWithItemsByPublicId(UUID publicId);

    /** Next version number for a blueprint = count of prior snapshots + 1. */
    long countBySourceBlueprintPublicId(UUID sourceBlueprintPublicId);

    /** Every question the tenant has put into an exam of its own, so a new exam can avoid repeating them. */
    @Query("select distinct i.sourceQuestionPublicId from ExamVersion v join v.items i where v.tenantId = :tenantId")
    List<UUID> findUsedQuestionIds(@Param("tenantId") UUID tenantId);

    /** Frozen items of any version the tenant owns, by item id (examiner prompt lookup). */
    @Query("select i from ExamVersion v join v.items i where i.publicId in :itemPublicIds and v.tenantId = :tenantId")
    List<ExamVersionItem> findItemsForTenant(@Param("itemPublicIds") Collection<UUID> itemPublicIds,
            @Param("tenantId") UUID tenantId);

    /** Audio prompt files among {@code refs} frozen into any exam version. */
    @Query("select item.audioPromptRef from ExamVersion version join version.items item "
            + "where item.audioPromptRef in :refs")
    List<UUID> findFrozenAudioPromptRefs(@Param("refs") Collection<UUID> refs);

    /** Image prompt files among {@code refs} frozen into any exam version. */
    @Query("select item.imagePromptRef from ExamVersion version join version.items item "
            + "where item.imagePromptRef in :refs")
    List<UUID> findFrozenImagePromptRefs(@Param("refs") Collection<UUID> refs);
}
