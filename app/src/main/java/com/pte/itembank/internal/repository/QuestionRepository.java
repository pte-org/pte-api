package com.pte.itembank.internal.repository;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.Visibility;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    @EntityGraph(attributePaths = "options")
    Optional<Question> findWithOptionsByPublicId(UUID publicId);

    /** SHARED bank + the caller's own PRIVATE items. */
    @EntityGraph(attributePaths = "options")
    @Query("SELECT q FROM Question q WHERE q.visibility = com.pte.itembank.domain.enums.Visibility.SHARED "
            + "OR q.tenantId = :tenantId")
    List<Question> findAccessible(@Param("tenantId") UUID tenantId);

    @EntityGraph(attributePaths = "options")
    @Query("SELECT q FROM Question q")
    List<Question> findAllWithOptions();

    @Query("SELECT q.pteTaskType, COUNT(q) FROM Question q "
            + "WHERE q.deleted = false AND q.visibility = :visibility "
            + "AND q.pteTaskType IN :taskTypes GROUP BY q.pteTaskType")
    List<Object[]> countByVisibilityAndTaskTypeIn(@Param("visibility") Visibility visibility,
                                                    @Param("taskTypes") Set<PteTaskType> taskTypes);
}
