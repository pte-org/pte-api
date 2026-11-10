package com.pte.identity.internal.service.passwordreset;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Runs reset requests off the request thread. The caller is answered before any lookup, token write or mail
 * happens, so response time cannot tell an address with an account from one without. The pool is small and
 * bounded and drops work when full: requests are already rate limited, and a dropped one is just an unsent mail.
 * The executor is private on purpose; exposing an {@code Executor} bean would switch off Spring Boot's default one.
 */
@Component
public class PasswordResetDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetDispatcher.class);
    private static final int CORE_THREADS = 2;
    private static final int MAX_THREADS = 4;
    private static final int QUEUE_CAPACITY = 200;

    private final PasswordResetService passwordResetService;
    private final Executor executor;
    private final ExecutorService ownedExecutor;

    /** Pool size and queue come from {@code app.password-reset.mail.*}; a full queue drops the request quietly. */
    @Autowired
    public PasswordResetDispatcher(PasswordResetService passwordResetService,
                                   @Value("${app.password-reset.mail.max-threads:" + MAX_THREADS + "}") int maxThreads,
                                   @Value("${app.password-reset.mail.queue-capacity:" + QUEUE_CAPACITY + "}")
                                   int queueCapacity) {
        this.passwordResetService = passwordResetService;
        this.ownedExecutor = new ThreadPoolExecutor(Math.min(CORE_THREADS, maxThreads), maxThreads, 60,
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(queueCapacity), runnable -> {
                    Thread thread = new Thread(runnable, "password-reset");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.DiscardPolicy());
        this.executor = ownedExecutor;
    }

    PasswordResetDispatcher(PasswordResetService passwordResetService, Executor executor) {
        this.passwordResetService = passwordResetService;
        this.executor = executor;
        this.ownedExecutor = null;
    }

    public void requestAsync(String email, String clientIp) {
        executor.execute(() -> {
            try {
                passwordResetService.request(email, clientIp);
            } catch (RuntimeException ex) {
                // Never log the address: it identifies the person who asked.
                log.warn("Password reset request failed: {}", ex.getClass().getSimpleName());
            }
        });
    }

    @PreDestroy
    void shutdown() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdown();
        }
    }
}
