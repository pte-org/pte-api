package com.pte.itembank.internal.repository;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionStatus;
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

    @EntityGraph(attributePaths = "options")
    @Query("SELECT q FROM Question q WHERE q.deleted = false "
            + "AND q.visibility = com.pte.itembank.domain.enums.Visibility.SHARED")
    List<Question> findAllWithOptions();

    @Query("SELECT q.pteTaskType, COUNT(q) FROM Question q "
            + "WHERE q.deleted = false AND q.visibility = :visibility "
            + "AND q.status = com.pte.itembank.domain.enums.QuestionStatus.APPROVED "
            + "AND q.pteTaskType IN :taskTypes GROUP BY q.pteTaskType")
    List<Object[]> countByVisibilityAndTaskTypeIn(@Param("visibility") Visibility visibility,
                                                    @Param("taskTypes") Set<PteTaskType> taskTypes);

    @Query("SELECT COUNT(q) FROM Question q WHERE q.deleted = false "
            + "AND q.visibility = com.pte.itembank.domain.enums.Visibility.SHARED "
            + "AND q.status = com.pte.itembank.domain.enums.QuestionStatus.APPROVED "
            + "AND q.pteTaskType = :taskType")
    long countAvailableByTaskType(@Param("taskType") PteTaskType taskType);

    @EntityGraph(attributePaths = "options")
    @Query(value = """
            SELECT q.*
            FROM questions q
            WHERE q.deleted = FALSE
              AND q.visibility = 'SHARED'
              AND q.status = 'APPROVED'
              AND q.pte_task_type = :taskType
            ORDER BY md5(CAST(q.id AS text) || CAST(:seed AS text)), q.id
            LIMIT :limit
            """, nativeQuery = true)
    List<Question> findRandomByTaskType(@Param("taskType") String taskType,
                                         @Param("limit") int limit,
                                         @Param("seed") long seed);
}
