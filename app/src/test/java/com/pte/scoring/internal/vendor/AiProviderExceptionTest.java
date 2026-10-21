package com.pte.scoring.internal.vendor;

import com.pte.scoring.domain.enums.AiFailureReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderExceptionTest {

    @Test
    void unclassifiedFailuresAreInternalErrorsAndNotRetried() {
        AiProviderException ex = new AiProviderException("legacy failure");

        assertThat(ex.getReason()).isEqualTo(AiFailureReason.INTERNAL_ERROR);
        assertThat(ex.getRetryAfter()).isNull();
        assertThat(ex.isTransient()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(AiFailureReason.class)
    void classifiedFailuresFollowTheirReason(AiFailureReason reason) {
        AiProviderException ex = new AiProviderException(reason, "classified failure");

        assertThat(ex.getReason()).isEqualTo(reason);
        assertThat(ex.isTransient()).isEqualTo(reason.isTransient());
    }

    @Test
    void onlyProviderSideHiccupsAreTransient() {
        assertThat(Arrays.stream(AiFailureReason.values()).filter(AiFailureReason::isTransient))
                .containsExactlyInAnyOrder(AiFailureReason.PROVIDER_RATE_LIMITED,
                        AiFailureReason.PROVIDER_UNAVAILABLE, AiFailureReason.PROVIDER_TIMEOUT,
                        AiFailureReason.PROVIDER_NETWORK_ERROR);
    }

    @Test
    void carriesTheProviderRetryHint() {
        AiProviderException ex = new AiProviderException(
                AiFailureReason.PROVIDER_RATE_LIMITED, "rate limited", Duration.ofSeconds(17));

        assertThat(ex.getRetryAfter()).isEqualTo(Duration.ofSeconds(17));
    }
}
