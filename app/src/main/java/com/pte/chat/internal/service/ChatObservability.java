package com.pte.chat.internal.service;

import com.pte.chat.internal.client.ChatAiFailureReason;
import com.pte.chat.internal.constant.ChatConstants;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Payload-free counters with bounded tags (status, cause, reason): never a question, an answer or a student id.
 * Watch {@code chat.quota.refunded}: refunded calls are free for the student but still cost provider tokens,
 * so a sudden rise means abuse or a provider problem.
 */
@Component
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatObservability {

    private final MeterRegistry meterRegistry;

    public ChatObservability(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void messageCounted(String status) {
        count("chat.message.counted", "status", status);
    }

    public void usageRecordFailed() {
        count("chat.usage.record.failed");
    }

    public void quotaRefunded(String cause) {
        count("chat.quota.refunded", "cause", cause);
    }

    public void refundFailed() {
        count("chat.quota.refund.failed");
    }

    public void providerFailed(ChatAiFailureReason reason) {
        count("chat.provider.failed", "reason", reason.name());
    }

    public void slotRejected() {
        count("chat.slot.rejected");
    }

    public void inFlightRejected() {
        count("chat.inflight.rejected");
    }

    private void count(String name, String... tags) {
        Counter.builder(name).tags(tags).register(meterRegistry).increment();
    }
}
