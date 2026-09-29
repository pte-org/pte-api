package com.pte.attempt.internal.service;

import com.pte.attempt.internal.repository.AttemptSecurityEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptSecurityAuditRetentionServiceTest {

    @Mock
    private AttemptSecurityEventRepository securityEventRepository;

    @Test
    void marksRowsOlderThanConfiguredRetentionWithoutHardDelete() {
        Instant now = Instant.parse("2026-09-28T04:00:00Z");
        when(securityEventRepository.markExpiredBefore(now.minus(180, ChronoUnit.DAYS))).thenReturn(3);
        AttemptSecurityAuditRetentionService service = new AttemptSecurityAuditRetentionService(
                securityEventRepository, 180);

        int marked = service.markExpired(now);

        assertThat(marked).isEqualTo(3);
        verify(securityEventRepository).markExpiredBefore(now.minus(180, ChronoUnit.DAYS));
    }
}
