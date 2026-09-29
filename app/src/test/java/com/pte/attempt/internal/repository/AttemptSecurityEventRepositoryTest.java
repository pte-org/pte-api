package com.pte.attempt.internal.repository;

import com.pte.attempt.domain.AttemptSecurityEvent;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.domain.enums.LockdownViolationSeverity;
import com.pte.attempt.domain.enums.LockdownViolationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class AttemptSecurityEventRepositoryTest {

    @Autowired
    private ExamAttemptRepository examAttemptRepository;

    @Autowired
    private AttemptSecurityEventRepository securityEventRepository;

    @Test
    void cursorQueryExcludesDeletedRows() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ExamAttempt attempt = persistAttempt(tenantId, sessionId);

        AttemptSecurityEvent event = event(attempt, "client-1", Instant.parse("2026-09-28T04:00:00Z"));
        securityEventRepository.saveAndFlush(event);

        assertThat(securityEventRepository.findByAttemptIdAndClientEventId(attempt.getId(), "client-1"))
                .contains(event);
        assertThat(securityEventRepository.findForSession(sessionId, tenantId, null, null,
                PageRequest.of(0, 10))).containsExactly(event);

        event.setDeleted(true);
        securityEventRepository.saveAndFlush(event);
        assertThat(securityEventRepository.findForSession(sessionId, tenantId, null, null,
                PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void clientEventIdIsUniqueWithinAnAttempt() {
        ExamAttempt attempt = persistAttempt(UUID.randomUUID(), UUID.randomUUID());
        securityEventRepository.saveAndFlush(event(attempt, "client-1",
                Instant.parse("2026-09-28T04:00:00Z")));

        AttemptSecurityEvent duplicate = event(attempt, "client-1", Instant.parse("2026-09-28T04:00:01Z"));
        assertThatThrownBy(() -> securityEventRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cursorQueryReturnsOnlyEventsAfterTheLastSeenTuple() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ExamAttempt attempt = persistAttempt(tenantId, sessionId);
        Instant firstDetectedAt = Instant.parse("2026-09-28T04:00:00Z");

        AttemptSecurityEvent first = event(attempt, "client-1", firstDetectedAt);
        first.setPublicId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        AttemptSecurityEvent second = event(attempt, "client-2", firstDetectedAt.plusSeconds(1));
        second.setPublicId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        securityEventRepository.saveAndFlush(first);
        securityEventRepository.saveAndFlush(second);

        assertThat(securityEventRepository.findForSession(sessionId, tenantId, firstDetectedAt,
                first.getPublicId(), PageRequest.of(0, 10))).containsExactly(second);
    }

    private ExamAttempt persistAttempt(UUID tenantId, UUID sessionId) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setPublicId(UUID.randomUUID());
        attempt.setTenantId(tenantId);
        attempt.setSessionPublicId(sessionId);
        attempt.setStudentPublicId(UUID.randomUUID());
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        return examAttemptRepository.saveAndFlush(attempt);
    }

    private AttemptSecurityEvent event(ExamAttempt attempt, String clientEventId, Instant detectedAt) {
        AttemptSecurityEvent event = new AttemptSecurityEvent();
        event.setAttempt(attempt);
        event.setAttemptPublicId(attempt.getPublicId());
        event.setSessionPublicId(attempt.getSessionPublicId());
        event.setStudentPublicId(attempt.getStudentPublicId());
        event.setTenantId(attempt.getTenantId());
        event.setClientEventId(clientEventId);
        event.setViolationType(LockdownViolationType.LOCKDOWN_FULLSCREEN_EXIT);
        event.setSeverity(LockdownViolationSeverity.WARNING);
        event.setDetail("detail");
        event.setDetectedAt(detectedAt);
        return event;
    }
}
