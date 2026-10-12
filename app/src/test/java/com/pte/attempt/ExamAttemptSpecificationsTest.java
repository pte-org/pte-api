package com.pte.attempt;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.attempt.internal.repository.ExamAttemptSpecifications;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ExamAttemptSpecificationsTest {

    @Autowired
    private ExamAttemptRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void appliesOptionalFiltersWithoutDroppingTenantStudentOrDeletedScope() {
        UUID tenantId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Instant olderAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant newerAt = Instant.parse("2026-02-01T00:00:00Z");
        Instant newestAt = Instant.parse("2026-03-01T00:00:00Z");

        ExamAttempt older = persist(tenantId, studentId, olderAt, AttemptStatus.CREATED, false);
        ExamAttempt newer = persist(tenantId, studentId, newerAt, AttemptStatus.SUBMITTED, false);
        ExamAttempt newest = persist(tenantId, studentId, newestAt, AttemptStatus.IN_PROGRESS, false);
        persist(tenantId, UUID.randomUUID(), Instant.parse("2026-04-01T00:00:00Z"),
                AttemptStatus.SUBMITTED, false);
        persist(UUID.randomUUID(), studentId, Instant.parse("2026-05-01T00:00:00Z"),
                AttemptStatus.SUBMITTED, false);
        persist(tenantId, studentId, Instant.parse("2026-06-01T00:00:00Z"), AttemptStatus.SUBMITTED, true);
        entityManager.clear();

        var all = repository.findAll(ExamAttemptSpecifications.forStudent(tenantId, studentId,
                        null, null, null), historyPage(0, 10));

        assertThat(all.getContent()).extracting(ExamAttempt::getPublicId)
                .containsExactly(newest.getPublicId(), newer.getPublicId(), older.getPublicId());
        assertThat(all.getTotalElements()).isEqualTo(3);

        var filtered = repository.findAll(ExamAttemptSpecifications.forStudent(tenantId, studentId,
                        newerAt, newestAt, AttemptStatus.SUBMITTED), historyPage(0, 10));

        assertThat(filtered.getContent()).extracting(ExamAttempt::getPublicId)
                .containsExactly(newer.getPublicId());

        var exclusiveUpperBound = repository.findAll(ExamAttemptSpecifications.forStudent(tenantId, studentId,
                        newerAt, newerAt, null), historyPage(0, 10));

        assertThat(exclusiveUpperBound.getContent()).isEmpty();

        var firstPage = repository.findAll(ExamAttemptSpecifications.forStudent(tenantId, studentId,
                        null, null, null), historyPage(0, 1));

        assertThat(firstPage.getContent()).extracting(ExamAttempt::getPublicId)
                .containsExactly(newest.getPublicId());
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.hasNext()).isTrue();
    }

    private ExamAttempt persist(UUID tenantId, UUID studentId, Instant createdAt, AttemptStatus status,
            boolean deleted) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setTenantId(tenantId);
        attempt.setStudentPublicId(studentId);
        attempt.setSessionPublicId(UUID.randomUUID());
        attempt.setAttemptNumber(1);
        attempt.setStatus(status);
        attempt.setCreatedAt(createdAt);
        attempt.setDeleted(deleted);
        ExamAttempt persisted = repository.saveAndFlush(attempt);
        entityManager.createNativeQuery("update exam_attempts set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", persisted.getId())
                .executeUpdate();
        return persisted;
    }

    private PageRequest historyPage(int page, int size) {
        return PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
}
