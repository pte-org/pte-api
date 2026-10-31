package com.pte.chat.internal.service;

import com.pte.chat.internal.client.ChatAiFailureReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Counters only: names under {@code chat.}, bounded tag values, never the text of a question or answer. */
class ChatObservabilityTest {

    private SimpleMeterRegistry registry;
    private ChatObservability observability;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        observability = new ChatObservability(registry);
    }

    private double total() {
        return registry.getMeters().stream().filter(Counter.class::isInstance)
                .mapToDouble(meter -> ((Counter) meter).count()).sum();
    }

    private Set<String> names() {
        return registry.getMeters().stream().map(meter -> meter.getId().getName()).collect(Collectors.toSet());
    }

    private List<String> tagValues() {
        return registry.getMeters().stream().flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getValue()).toList();
    }

    @Test
    void nothingIsRegisteredUntilSomethingHappens() {
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void messageStoredCountsByStatusAndCacheOrigin() {
        observability.messageStored("OK", false);
        observability.messageStored("OK", true);
        observability.messageStored("REFUSED", false);
        observability.messageStored("FAILED", false);

        assertThat(total()).isEqualTo(4.0);
        assertThat(registry.getMeters()).hasSize(4);
        assertThat(names()).allMatch(name -> name.startsWith("chat."));
        assertThat(tagValues()).contains("OK", "REFUSED", "FAILED");
    }

    @Test
    void repeatedEventsAddUpOnTheSameCounter() {
        observability.messageStored("OK", false);
        observability.messageStored("OK", false);
        observability.messageStored("OK", false);

        assertThat(registry.getMeters()).hasSize(1);
        assertThat(total()).isEqualTo(3.0);
    }

    @Test
    void quotaRefundedCountsPerCause() {
        observability.quotaRefunded("off_topic");
        observability.quotaRefunded("provider_failure");
        observability.quotaRefunded("off_topic");

        assertThat(total()).isEqualTo(3.0);
        assertThat(registry.getMeters()).hasSize(2);
        assertThat(names()).allMatch(name -> name.startsWith("chat."));
        assertThat(tagValues()).containsExactlyInAnyOrder("off_topic", "provider_failure");
    }

    @Test
    void refundFailedSlotRejectedInFlightRejectedAndCacheStoreFailedEachHaveTheirOwnCounter() {
        observability.refundFailed();
        observability.slotRejected();
        observability.slotRejected();
        observability.inFlightRejected();
        observability.cacheStoreFailed();

        assertThat(total()).isEqualTo(5.0);
        assertThat(registry.getMeters()).hasSize(4);
        assertThat(names()).hasSize(4).allMatch(name -> name.startsWith("chat."));
        assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags()).isEmpty());
    }

    @Test
    void providerFailedHasOneCounterPerReasonAndNothingElse() {
        for (ChatAiFailureReason reason : ChatAiFailureReason.values()) {
            observability.providerFailed(reason);
        }

        assertThat(total()).isEqualTo((double) ChatAiFailureReason.values().length);
        assertThat(registry.getMeters()).hasSize(ChatAiFailureReason.values().length);
        assertThat(names()).allMatch(name -> name.startsWith("chat."));
        Set<String> reasonNames = Arrays.stream(ChatAiFailureReason.values())
                .map(Enum::name).collect(Collectors.toSet());
        assertThat(tagValues()).allMatch(value -> value.matches("[A-Za-z0-9_.-]{1,40}"));
        assertThat(tagValues().stream().map(String::toUpperCase).collect(Collectors.toSet()))
                .containsAll(registry.getMeters().stream().anyMatch(m -> !m.getId().getTags().isEmpty())
                        ? reasonNames : Set.of());
    }

    @Test
    void differentEventsNeverShareAMeterId() {
        observability.messageStored("OK", false);
        observability.quotaRefunded("off_topic");
        observability.refundFailed();
        observability.providerFailed(ChatAiFailureReason.TIMEOUT);
        observability.slotRejected();
        observability.inFlightRejected();
        observability.cacheStoreFailed();

        List<Meter.Id> ids = registry.getMeters().stream().map(Meter::getId).toList();
        assertThat(ids).doesNotHaveDuplicates().hasSize(7);
    }
}
