package com.pte.scoring.internal.listener;

import com.pte.attempt.dto.event.AttemptSubmittedEvent;
import com.pte.scoring.internal.service.ScoringIngestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AttemptSubmittedIngestListenerTest {

    @Mock
    private ScoringIngestService ingestService;
    @InjectMocks
    private AttemptSubmittedIngestListener listener;

    private final UUID attemptPublicId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final AttemptSubmittedEvent event = new AttemptSubmittedEvent(attemptPublicId, UUID.randomUUID(), tenantId);

    @Test
    void ingestsTheSubmittedAttempt() {
        listener.onAttemptSubmitted(event);

        verify(ingestService).ingestSubmittedAttempt(attemptPublicId, tenantId);
    }

    @Test
    void ingestFailureNeverPropagatesToTheSubmission() {
        doThrow(new IllegalStateException("db down")).when(ingestService)
                .ingestSubmittedAttempt(attemptPublicId, tenantId);

        assertThatCode(() -> listener.onAttemptSubmitted(event)).doesNotThrowAnyException();
    }
}
