package com.pte.identity.internal.service.passwordreset;

import com.pte.identity.internal.config.PasswordResetProperties;
import com.pte.identity.internal.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Expired reset links are removed once they are older than the retention period, never sooner. */
@ExtendWith(MockitoExtension.class)
class PasswordResetTokenPurgeServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-01T03:30:00Z");

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Test
    void purgeExpired_deletesLinksThatExpiredBeforeNowMinusTheRetention() {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setRetentionDays(7);
        PasswordResetTokenPurgeService service = new PasswordResetTokenPurgeService(tokenRepository,
                Clock.fixed(NOW, ZoneOffset.UTC), properties);
        when(tokenRepository.deleteExpiredBefore(Instant.parse("2026-10-25T03:30:00Z"))).thenReturn(4);

        assertThat(service.purgeExpired()).isEqualTo(4);

        verify(tokenRepository).deleteExpiredBefore(Instant.parse("2026-10-25T03:30:00Z"));
    }
}
