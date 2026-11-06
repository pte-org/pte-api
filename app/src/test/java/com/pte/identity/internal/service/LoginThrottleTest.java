package com.pte.identity.internal.service;

import com.pte.identity.internal.security.TokenHasher;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Five wrong passwords per account and address in fifteen minutes; the user name never reaches Redis in clear. */
@ExtendWith(MockitoExtension.class)
class LoginThrottleTest {

    @Mock
    private ProxyManager<String> proxyManager;
    @Mock
    private BucketProxy bucket;

    private final TokenHasher tokenHasher = new TokenHasher();
    private LoginThrottle throttle;

    @BeforeEach
    void setUp() {
        throttle = new LoginThrottle(proxyManager, tokenHasher);
    }

    @SuppressWarnings("unchecked")
    private void stubBucket() {
        when(proxyManager.getProxy(any(String.class), any(Supplier.class))).thenReturn(bucket);
    }

    @Test
    void isNotLockedWhileFailuresRemain() {
        stubBucket();
        when(bucket.getAvailableTokens()).thenReturn(1L);

        assertThat(throttle.isLocked("host@x.vn", "203.0.113.9")).isFalse();
    }

    @Test
    void isLockedWhenTheBudgetIsUsedUp() {
        stubBucket();
        when(bucket.getAvailableTokens()).thenReturn(0L);

        assertThat(throttle.isLocked("host@x.vn", "203.0.113.9")).isTrue();
    }

    @Test
    void aFailureTakesOneFromTheBudget() {
        stubBucket();

        throttle.recordFailure("host@x.vn", "203.0.113.9");

        verify(bucket).tryConsume(1);
    }

    @Test
    void aCorrectSignInRemovesTheBucket() {
        throttle.clear("host@x.vn", "203.0.113.9");

        verify(proxyManager).removeProxy(any(String.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void keysCarryTheAddressAndNeverTheUserNameInClear() {
        stubBucket();
        when(bucket.getAvailableTokens()).thenReturn(5L);

        throttle.isLocked("Host@X.vn", "203.0.113.9");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(proxyManager).getProxy(key.capture(), any(Supplier.class));
        assertThat(key.getValue()).contains("203.0.113.9").doesNotContainIgnoringCase("host@x.vn");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theUserNameIsCaseInsensitive() {
        stubBucket();
        when(bucket.getAvailableTokens()).thenReturn(5L);

        throttle.isLocked("HOST@x.vn", "203.0.113.9");
        throttle.isLocked("host@x.vn", "203.0.113.9");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(proxyManager, org.mockito.Mockito.times(2)).getProxy(key.capture(), any(Supplier.class));
        assertThat(key.getAllValues().get(0)).isEqualTo(key.getAllValues().get(1));
    }
}
