package com.pte.practice;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Bounded, payload-free metrics for practice auth and entitlement decisions. */
@Component
public class PracticeObservability {

    private static final String AUTH_CHALLENGE = "practice.auth.challenge";
    private static final String ENTITLEMENT_DECISION = "practice.entitlement.decision";

    private final MeterRegistry meterRegistry;

    public PracticeObservability(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void challengeRequested() {
        increment(AUTH_CHALLENGE, "outcome", "requested");
    }

    public void challengeRateLimited() {
        increment(AUTH_CHALLENGE, "outcome", "rate_limited");
    }

    public void challengeRejected() {
        increment(AUTH_CHALLENGE, "outcome", "rejected");
    }

    public void challengeVerified() {
        increment(AUTH_CHALLENGE, "outcome", "verified");
    }

    public void entitlementEvaluated(PracticeEntitlementState state) {
        if (state != null) {
            increment(ENTITLEMENT_DECISION, "state", state.name());
        }
    }

    private void increment(String name, String tagKey, String tagValue) {
        Counter.builder(name)
                .description("Practice rollout decision")
                .tag(tagKey, tagValue)
                .register(meterRegistry)
                .increment();
    }
}
