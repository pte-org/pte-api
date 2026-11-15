package com.pte.identity.internal.service.passwordreset;

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
 * Limits reset requests per e-mail, per username and per client address, so the form cannot be used to flood one
 * inbox or one account, or to probe many addresses. Keys carry only a hash of the e-mail or username: Redis never
 * holds either in clear.
 */
@Component
public class PasswordResetThrottle {

    private static final int WINDOW_MINUTES = 15;
    static final int EMAIL_LIMIT = 3;
    static final int IP_LIMIT = 10;
    static final Duration WINDOW = Duration.ofMinutes(WINDOW_MINUTES);

    private final ProxyManager<String> proxyManager;
    private final TokenHasher tokenHasher;
    private final int emailLimit;
    private final int ipLimit;
    private final Duration window;

    PasswordResetThrottle(ProxyManager<String> proxyManager, TokenHasher tokenHasher) {
        this(proxyManager, tokenHasher, EMAIL_LIMIT, IP_LIMIT, WINDOW);
    }

    /** The limits come from {@code app.password-reset.throttle.*}; the constants are their defaults. */
    @Autowired
    public PasswordResetThrottle(ProxyManager<String> proxyManager, TokenHasher tokenHasher,
                                 @Value("${app.password-reset.throttle.email-limit:" + EMAIL_LIMIT + "}") int emailLimit,
                                 @Value("${app.password-reset.throttle.ip-limit:" + IP_LIMIT + "}") int ipLimit,
                                 @Value("${app.password-reset.throttle.window:" + WINDOW_MINUTES + "m}")
                                 Duration window) {
        this.proxyManager = proxyManager;
        this.tokenHasher = tokenHasher;
        this.emailLimit = emailLimit;
        this.ipLimit = ipLimit;
        this.window = window;
    }

    /**
     * Takes one request from the e-mail, username and address budgets; false means the caller is over a limit and
     * should be ignored. The username budget has the same limit as the e-mail one and is keyed by a hash of the
     * lower-cased name, so one account cannot be flooded from many addresses.
     */
    public boolean tryAcquire(String normalizedEmail, String username, String clientIp) {
        boolean withinEmail = bucket("pwreset:email:" + tokenHasher.hash(normalizedEmail), emailLimit).tryConsume(1);
        boolean withinUsername =
                bucket("pwreset:user:" + tokenHasher.hash(username.trim().toLowerCase(Locale.ROOT)), emailLimit)
                        .tryConsume(1);
        return withinEmail && withinUsername && bucket("pwreset:ip:" + clientIp, ipLimit).tryConsume(1);
    }

    private Bucket bucket(String key, int limit) {
        return proxyManager.getProxy(key, () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(limit, window))
                .build());
    }
}
