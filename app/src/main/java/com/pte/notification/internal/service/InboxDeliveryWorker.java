package com.pte.notification.internal.service;

import com.pte.notification.domain.enums.InboxDeliveryStatus;
import com.pte.notification.internal.repository.InboxDeliveryClaim;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Durable database work only: no SMTP, RabbitMQ, or remote calls inside delivery transactions. */
@Component
@ConditionalOnProperty(prefix = "notification.inbox", name = "enabled", havingValue = "true")
public class InboxDeliveryWorker {
    private static final Logger LOG = LoggerFactory.getLogger(InboxDeliveryWorker.class);
    private final InboxDeliveryService service;
    private final MeterRegistry metrics;
    public InboxDeliveryWorker(InboxDeliveryService service, MeterRegistry metrics) {
        this.service = service; this.metrics = metrics;
        for (InboxDeliveryStatus status : InboxDeliveryStatus.values()) {
            Gauge.builder("notification.inbox.delivery.count", service, value -> value.countByStatus(status))
                    .tag("status", status.name()).register(metrics);
        }
    }

    @Scheduled(fixedDelayString = "${notification.inbox.poll-delay-ms:1000}")
    public void tick() {
        try {
            for (InboxDeliveryClaim claim : service.claimBatch()) {
                try {
                    boolean processed = service.deliver(claim);
                    metrics.counter("notification.inbox.worker", "outcome", processed ? "processed" : "stale").increment();
                } catch (RuntimeException failure) {
                    metrics.counter("notification.inbox.worker", "outcome", "failure").increment();
                    // Raw messages may contain SQL or content. Persist only classification codes.
                    LOG.warn("Inbox delivery failed: id={}, errorType={}", claim.publicId(), failure.getClass().getSimpleName());
                    try { service.fail(claim, failure); }
                    catch (RuntimeException recoveryFailure) {
                        LOG.warn("Inbox failure persistence deferred to lease recovery: id={}, errorType={}",
                                claim.publicId(), recoveryFailure.getClass().getSimpleName());
                    }
                }
            }
        } catch (RuntimeException unavailable) {
            metrics.counter("notification.inbox.worker", "outcome", "claim-failure").increment();
            LOG.warn("Inbox claim deferred: errorType={}", unavailable.getClass().getSimpleName());
        }
    }
}
