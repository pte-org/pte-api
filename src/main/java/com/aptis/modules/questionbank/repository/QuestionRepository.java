package com.aptis.modules.questionbank.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptis.modules.questionbank.domain.Question;

public interface QuestionRepository
        extends JpaRepository<Question, Long>, JpaSpecificationExecutor<Question> {

    Optional<Question> findByPublicId(UUID publicId);

    Optional<Question> findByPublicIdAndIsCurrentTrue(UUID publicId);


    /**
     * Deactivate all versions in a parent chain so that a new version
     * can be marked as the sole current version.
     */
    @Modifying
    @Query("UPDATE Question q SET q.isCurrent = false "
            + "WHERE q.id = :rootId OR q.parentId = :rootId")
    void deactivateVersionChain(@Param("rootId") Long rootId);
}
