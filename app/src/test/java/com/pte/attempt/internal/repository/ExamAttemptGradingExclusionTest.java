package com.pte.attempt.internal.repository;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The host's exclusion of an attempt from grading is a targeted update on the attempt: it records the reason and
 * leaves the version and update time alone, so the preview version the host confirmed stays valid.
 */
@DataJpaTest
class ExamAttemptGradingExclusionTest {

    @Autowired
    private ExamAttemptRepository attempts;

    @Autowired
    private EntityManager entityManager;

    @Test
    void anAttemptIsIncludedInGradingUntilTheHostExcludesIt() {
        ExamAttempt attempt = attempts.saveAndFlush(attempt(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(attempt.isGradingExcluded()).isFalse();
        assertThat(attempt.getGradingExclusionReason()).isNull();
    }

    @Test
    void excludingStoresTheReasonWithoutMovingTheVersionOrUpdateTime() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ExamAttempt saved = attempts.saveAndFlush(attempt(tenantId, sessionId));
        Long versionBefore = saved.getVersion();
        var updatedBefore = saved.getUpdatedAt();

        int updated = attempts.markGradingExcluded(saved.getPublicId(), tenantId, sessionId, "Student withdrew.");
        entityManager.clear();

        ExamAttempt reloaded = attempts.findById(saved.getId()).orElseThrow();
        assertThat(updated).isEqualTo(1);
        assertThat(reloaded.isGradingExcluded()).isTrue();
        assertThat(reloaded.getGradingExclusionReason()).isEqualTo("Student withdrew.");
        assertThat(reloaded.getVersion()).isEqualTo(versionBefore);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedBefore);
    }

    @Test
    void excludingNeverTouchesAnAttemptOfAnotherTenantOrSession() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ExamAttempt saved = attempts.saveAndFlush(attempt(tenantId, sessionId));

        int otherTenant = attempts.markGradingExcluded(saved.getPublicId(), UUID.randomUUID(), sessionId, "x");
        int otherSession = attempts.markGradingExcluded(saved.getPublicId(), tenantId, UUID.randomUUID(), "x");
        entityManager.clear();

        assertThat(otherTenant).isZero();
        assertThat(otherSession).isZero();
        assertThat(attempts.findById(saved.getId()).orElseThrow().isGradingExcluded()).isFalse();
    }

    private ExamAttempt attempt(UUID tenantId, UUID sessionId) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setTenantId(tenantId);
        attempt.setSessionPublicId(sessionId);
        attempt.setStudentPublicId(UUID.randomUUID());
        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setExamVersionPublicId(UUID.randomUUID());
        attempt.setExamDefinitionPublicId(UUID.randomUUID());
        return attempt;
    }
}
