package com.pte.identity.internal.service.passwordreset;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A reset request is limited per e-mail and per client address; the e-mail never reaches Redis in clear. */
@ExtendWith(MockitoExtension.class)
class PasswordResetThrottleTest {

    @Mock
    private ProxyManager<String> proxyManager;
    @Mock
    private BucketProxy emailBucket;
    @Mock
    private BucketProxy ipBucket;

    private final TokenHasher tokenHasher = new TokenHasher();
    private PasswordResetThrottle throttle;

    @BeforeEach
    void setUp() {
        throttle = new PasswordResetThrottle(proxyManager, tokenHasher);
    }

    @Test
    void allowsTheRequestWhenBothBucketsHaveRoom() {
        stubBuckets();
        when(emailBucket.tryConsume(1)).thenReturn(true);
        when(ipBucket.tryConsume(1)).thenReturn(true);

        assertThat(throttle.tryAcquire("host@x.vn", "203.0.113.9")).isTrue();
    }

    @Test
    void refusesWhenTheEmailBucketIsEmpty() {
        stubBuckets();
        when(emailBucket.tryConsume(1)).thenReturn(false);

        assertThat(throttle.tryAcquire("host@x.vn", "203.0.113.9")).isFalse();
        verify(ipBucket, never()).tryConsume(1);
    }

    @Test
    void refusesWhenTheAddressBucketIsEmpty() {
        stubBuckets();
        when(emailBucket.tryConsume(1)).thenReturn(true);
        when(ipBucket.tryConsume(1)).thenReturn(false);

        assertThat(throttle.tryAcquire("host@x.vn", "203.0.113.9")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void keysNeverContainTheEmailInClear() {
        stubBuckets();
        when(emailBucket.tryConsume(1)).thenReturn(true);
        when(ipBucket.tryConsume(1)).thenReturn(true);

        throttle.tryAcquire("host@x.vn", "203.0.113.9");

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(proxyManager, org.mockito.Mockito.times(2)).getProxy(keys.capture(), any(Supplier.class));
        assertThat(keys.getAllValues()).noneMatch(key -> key.contains("host@x.vn"));
        assertThat(keys.getAllValues()).anyMatch(key -> key.contains(tokenHasher.hash("host@x.vn")));
        assertThat(keys.getAllValues()).anyMatch(key -> key.contains("203.0.113.9"));
    }

    @SuppressWarnings("unchecked")
    private void stubBuckets() {
        when(proxyManager.getProxy(org.mockito.ArgumentMatchers.contains(tokenHasher.hash("host@x.vn")),
                any(Supplier.class))).thenReturn(emailBucket);
        lenient().when(proxyManager.getProxy(org.mockito.ArgumentMatchers.contains("203.0.113.9"),
                any(Supplier.class))).thenReturn(ipBucket);
    }

}
