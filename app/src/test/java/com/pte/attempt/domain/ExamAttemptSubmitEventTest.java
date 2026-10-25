package com.pte.attempt.domain;

import com.pte.attempt.dto.event.AttemptSubmittedEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExamAttemptSubmitEventTest {

    @Test
    void submitRegistersOneEventThatIsPublishedOnceAndThenCleared() throws Exception {
        ExamAttempt attempt = new ExamAttempt();
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        attempt.setSessionPublicId(sessionPublicId);
        attempt.setTenantId(tenantId);

        attempt.submit(java.time.Instant.now());

        Collection<?> events = domainEvents(attempt);
        assertThat(events).hasSize(1);
        AttemptSubmittedEvent event = (AttemptSubmittedEvent) events.iterator().next();
        assertThat(event.attemptPublicId()).isEqualTo(attempt.getPublicId());
        assertThat(event.sessionPublicId()).isEqualTo(sessionPublicId);
        assertThat(event.tenantId()).isEqualTo(tenantId);

        Method clear = ExamAttempt.class.getDeclaredMethod("clearDomainEvents");
        clear.setAccessible(true);
        clear.invoke(attempt);
        assertThat(domainEvents(attempt)).isEmpty();
    }

    @Test
    void attemptWithoutSubmitPublishesNothing() throws Exception {
        assertThat(domainEvents(new ExamAttempt())).isEmpty();
    }

    private static Collection<?> domainEvents(ExamAttempt attempt) throws Exception {
        Method method = ExamAttempt.class.getDeclaredMethod("domainEvents");
        method.setAccessible(true);
        return (Collection<?>) method.invoke(attempt);
    }
}
