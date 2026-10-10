package com.pte.reporting.internal.repository;

import com.pte.reporting.domain.AttemptReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Sort;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AttemptReportSpecificationsTest {

    @Autowired
    private AttemptReportRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void appliesOptionalDateFiltersWithoutDroppingPublishedSnapshotScope() {
        UUID tenantId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Instant olderAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant newerAt = Instant.parse("2026-02-01T00:00:00Z");
        Instant newestAt = Instant.parse("2026-03-01T00:00:00Z");

        AttemptReport older = persist(tenantId, studentId, olderAt, true, true);
        AttemptReport newer = persist(tenantId, studentId, newerAt, true, true);
        AttemptReport newest = persist(tenantId, studentId, newestAt, true, true);
        persist(tenantId, studentId, Instant.parse("2026-04-01T00:00:00Z"), false, true);
        persist(tenantId, studentId, Instant.parse("2026-05-01T00:00:00Z"), true, false);
        AttemptReport deleted = persist(tenantId, studentId, Instant.parse("2026-06-01T00:00:00Z"), true, true);
        deleted.setDeleted(true);
        repository.saveAndFlush(deleted);
        persist(UUID.randomUUID(), studentId, Instant.parse("2026-06-01T00:00:00Z"), true, true);
        persist(tenantId, UUID.randomUUID(), Instant.parse("2026-07-01T00:00:00Z"), true, true);
        entityManager.clear();

        var all = repository.findAll(AttemptReportSpecifications.forStudent(tenantId, studentId,
                        null, null), Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id")));

        assertThat(all).extracting(AttemptReport::getPublicId)
                .containsExactly(newest.getPublicId(), newer.getPublicId(), older.getPublicId());

        var filtered = repository.findAll(AttemptReportSpecifications.forStudent(tenantId, studentId,
                        newerAt, newestAt), Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id")));

        assertThat(filtered).extracting(AttemptReport::getPublicId).containsExactly(newer.getPublicId());
    }

    private AttemptReport persist(UUID tenantId, UUID studentId, Instant publishedAt,
            boolean published, boolean hasSnapshot) {
        AttemptReport report = new AttemptReport();
        report.setTenantId(tenantId);
        report.setStudentPublicId(studentId);
        report.setAttemptPublicId(UUID.randomUUID());
        report.setSessionPublicId(UUID.randomUUID());
        report.setPublished(published);
        report.setPublishedAt(publishedAt);
        report.setReportSnapshotJson(hasSnapshot ? "{}" : null);
        AttemptReport persisted = repository.saveAndFlush(report);
        entityManager.createNativeQuery("update attempt_reports set published_at = :publishedAt where id = :id")
                .setParameter("publishedAt", publishedAt)
                .setParameter("id", persisted.getId())
                .executeUpdate();
        return persisted;
    }
}
