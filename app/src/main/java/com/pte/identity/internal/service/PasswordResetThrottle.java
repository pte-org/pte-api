package com.pte.identity.internal.service;

import com.pte.identity.internal.security.TokenHasher;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Limits reset requests per e-mail and per client address, so the form cannot be used to flood one inbox or to
 * probe many addresses. Keys carry only a hash of the e-mail: Redis never holds an address in clear.
 */
@Component
public class PasswordResetThrottle {

    static final int EMAIL_LIMIT = 3;
    static final int IP_LIMIT = 10;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final ProxyManager<String> proxyManager;
    private final TokenHasher tokenHasher;

    public PasswordResetThrottle(ProxyManager<String> proxyManager, TokenHasher tokenHasher) {
        this.proxyManager = proxyManager;
        this.tokenHasher = tokenHasher;
    }

    /** Takes one request from both budgets; false means the caller is over a limit and should be ignored. */
    public boolean tryAcquire(String normalizedEmail, String clientIp) {
        return bucket("pwreset:email:" + tokenHasher.hash(normalizedEmail), EMAIL_LIMIT).tryConsume(1)
                && bucket("pwreset:ip:" + clientIp, IP_LIMIT).tryConsume(1);
    }

    private Bucket bucket(String key, int limit) {
        return proxyManager.getProxy(key, () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(limit, WINDOW))
                .build());
    }
}
