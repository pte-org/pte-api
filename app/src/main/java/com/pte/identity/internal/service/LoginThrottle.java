package com.pte.identity.internal.service;

import com.pte.identity.internal.security.TokenHasher;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Temporary protection against password guessing: after {@value #FAILURE_LIMIT} wrong passwords for one account from
 * one address within {@value #WINDOW_MINUTES} minutes (both set by {@code app.security.login.*}), sign-in is refused until the window ends. The budget is per
 * account and address, so a stranger cannot lock the owner out from another address. Keys carry only a hash of the
 * user name: Redis never holds it in clear.
 */
@Component
public class LoginThrottle {

    static final int FAILURE_LIMIT = 5;
    static final int WINDOW_MINUTES = 15;
    static final Duration WINDOW = Duration.ofMinutes(WINDOW_MINUTES);

    private final ProxyManager<String> proxyManager;
    private final TokenHasher tokenHasher;
    private final int failureLimit;
    private final Duration window;

    LoginThrottle(ProxyManager<String> proxyManager, TokenHasher tokenHasher) {
        this(proxyManager, tokenHasher, FAILURE_LIMIT, WINDOW);
    }

    @Autowired
    public LoginThrottle(ProxyManager<String> proxyManager, TokenHasher tokenHasher,
                         @Value("${app.security.login.failure-limit:" + FAILURE_LIMIT + "}") int failureLimit,
                         @Value("${app.security.login.window:" + WINDOW_MINUTES + "m}") Duration window) {
        this.proxyManager = proxyManager;
        this.tokenHasher = tokenHasher;
        this.failureLimit = failureLimit;
        this.window = window;
    }

    /** True while the account has used up its failures for this address. */
    public boolean isLocked(String username, String clientIp) {
        return bucket(username, clientIp).getAvailableTokens() < 1;
    }

    /** Takes one failure from the budget. */
    public void recordFailure(String username, String clientIp) {
        bucket(username, clientIp).tryConsume(1);
    }

    /** A correct sign-in gives the full budget back. */
    public void clear(String username, String clientIp) {
        proxyManager.removeProxy(key(username, clientIp));
    }

    private Bucket bucket(String username, String clientIp) {
        return proxyManager.getProxy(key(username, clientIp), () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder().capacity(failureLimit).refillIntervally(failureLimit, window).build())
                .build());
    }

    private String key(String username, String clientIp) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return "login:fail:" + tokenHasher.hash(normalized) + ":" + (clientIp == null ? "unknown" : clientIp);
    }
}
