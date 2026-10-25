package com.pte.proctoring.internal.repository;

import com.pte.proctoring.domain.ExamIncident;
import com.pte.proctoring.domain.enums.IncidentOrigin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ExamIncidentRepositoryTest {

    private static final Set<IncidentOrigin> EVIDENCE = EnumSet.of(IncidentOrigin.AUTO, IncidentOrigin.PROCTOR_FLAG);

    @Autowired
    private ExamIncidentRepository repository;

    private ExamIncident incident(UUID tenantId, UUID sessionId, UUID attemptId, IncidentOrigin origin,
                                  String clientEventId, Instant detectedAt) {
        ExamIncident incident = new ExamIncident();
        incident.setTenantId(tenantId);
        incident.setSessionPublicId(sessionId);
        incident.setAttemptPublicId(attemptId);
        incident.setOrigin(origin);
        incident.setType("LOCKDOWN_FULLSCREEN_EXIT");
        incident.setTitle("title");
        incident.setSeverity("WARNING");
        incident.setClientEventId(clientEventId);
        incident.setDetectedAt(detectedAt);
        return incident;
    }

    @Test
    void securityAuditCursorQueryExcludesDeletedRowsAndReportedIncidents() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        ExamIncident auto = repository.saveAndFlush(incident(tenantId, sessionId, attemptId, IncidentOrigin.AUTO,
                "client-1", Instant.parse("2026-09-28T04:00:00Z")));
        repository.saveAndFlush(incident(tenantId, sessionId, attemptId, IncidentOrigin.REPORTED, null,
                Instant.parse("2026-09-28T04:00:01Z")));

        assertThat(repository.findAuditFirstPage(sessionId, tenantId, EVIDENCE, PageRequest.of(0, 10)))
                .containsExactly(auto);

        auto.setDeleted(true);
        repository.saveAndFlush(auto);
        assertThat(repository.findAuditFirstPage(sessionId, tenantId, EVIDENCE, PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    void clientEventIdIsUniqueWithinAnAttempt() {
        UUID attemptId = UUID.randomUUID();
        repository.saveAndFlush(incident(UUID.randomUUID(), UUID.randomUUID(), attemptId, IncidentOrigin.AUTO,
                "client-1", Instant.parse("2026-09-28T04:00:00Z")));

        ExamIncident duplicate = incident(UUID.randomUUID(), UUID.randomUUID(), attemptId, IncidentOrigin.AUTO,
                "client-1", Instant.parse("2026-09-28T04:00:01Z"));
        assertThatThrownBy(() -> repository.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rowsWithoutAClientEventIdNeverCollide() {
        UUID attemptId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        repository.saveAndFlush(incident(tenantId, sessionId, attemptId, IncidentOrigin.PROCTOR_FLAG, null,
                Instant.parse("2026-09-28T04:00:00Z")));
        repository.saveAndFlush(incident(tenantId, sessionId, attemptId, IncidentOrigin.PROCTOR_FLAG, null,
                Instant.parse("2026-09-28T04:00:01Z")));

        assertThat(repository.findBySessionPublicIdAndTenantIdAndOriginAndDeletedFalseOrderByDetectedAtAsc(
                sessionId, tenantId, IncidentOrigin.PROCTOR_FLAG)).hasSize(2);
    }

    @Test
    void cursorQueryReturnsOnlyRowsAfterTheLastSeenTuple() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        Instant firstDetectedAt = Instant.parse("2026-09-28T04:00:00Z");
        ExamIncident first = incident(tenantId, sessionId, attemptId, IncidentOrigin.AUTO, "client-1", firstDetectedAt);
        first.setPublicId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        ExamIncident second = incident(tenantId, sessionId, attemptId, IncidentOrigin.PROCTOR_FLAG, null,
                firstDetectedAt.plusSeconds(1));
        second.setPublicId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        repository.saveAndFlush(first);
        repository.saveAndFlush(second);

        assertThat(repository.findAuditAfter(sessionId, tenantId, EVIDENCE, firstDetectedAt,
                first.getPublicId(), PageRequest.of(0, 10))).containsExactly(second);
    }

    @Test
    void flagCountsGroupByAttemptAndIgnoreOtherOrigins() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        Instant at = Instant.parse("2026-09-28T04:00:00Z");
        repository.saveAndFlush(incident(tenantId, sessionId, attemptId, IncidentOrigin.PROCTOR_FLAG, null, at));
        repository.saveAndFlush(incident(tenantId, sessionId, attemptId, IncidentOrigin.PROCTOR_FLAG, null, at));
        repository.saveAndFlush(incident(tenantId, sessionId, attemptId, IncidentOrigin.AUTO, "c", at));

        List<Object[]> counts = repository.countFlagsByAttemptPublicIdIn(sessionId, tenantId, List.of(attemptId));

        assertThat(counts).hasSize(1);
        assertThat(counts.get(0)[0]).isEqualTo(attemptId);
        assertThat(counts.get(0)[1]).isEqualTo(2L);
    }

    @Test
    void retentionMarksOnlyOldAutoEvidence() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        Instant old = Instant.parse("2026-01-01T00:00:00Z");
        ExamIncident oldAuto = repository.saveAndFlush(incident(tenantId, sessionId, attemptId, IncidentOrigin.AUTO,
                "c1", old));
        ExamIncident oldReported = repository.saveAndFlush(incident(tenantId, sessionId, attemptId,
                IncidentOrigin.REPORTED, null, old));
        ExamIncident recentAuto = repository.saveAndFlush(incident(tenantId, sessionId, attemptId,
                IncidentOrigin.AUTO, "c2", Instant.parse("2026-09-01T00:00:00Z")));

        int marked = repository.markExpiredEvidenceBefore(Instant.parse("2026-06-01T00:00:00Z"));
        repository.flush();

        assertThat(marked).isEqualTo(1);
        assertThat(repository.findById(oldAuto.getId())).get().extracting(ExamIncident::isDeleted).isEqualTo(true);
        assertThat(repository.findById(oldReported.getId())).get().extracting(ExamIncident::isDeleted)
                .isEqualTo(false);
        assertThat(repository.findById(recentAuto.getId())).get().extracting(ExamIncident::isDeleted)
                .isEqualTo(false);
    }
}
