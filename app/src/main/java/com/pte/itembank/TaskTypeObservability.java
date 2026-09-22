package com.pte.itembank;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Bounded, payload-free metrics for the dynamic task-type rollout. Metric
 * names are stable operational identifiers; no user input is used as a tag.
 */
@Component
public class TaskTypeObservability {

    public static final String DYNAMIC_TASK_TYPE_CREATE = "dynamic_task_type_create_total";
    public static final String DYNAMIC_TASK_TYPE_DUPLICATE = "dynamic_task_type_duplicate_total";
    public static final String TEMPLATE_READINESS_FAILURE = "template_readiness_failure_total";
    public static final String UNSUPPORTED_RUNTIME_PREFLIGHT = "unsupported_runtime_preflight_total";
    public static final String LEGACY_TASK_TYPE_ADAPTER = "legacy_task_type_adapter_total";
    public static final String SNAPSHOT_RUNTIME_CONTRACT_FAILURE = "snapshot_runtime_contract_failure_total";

    private final MeterRegistry meterRegistry;

    public TaskTypeObservability(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void dynamicTaskTypeCreated() {
        increment(DYNAMIC_TASK_TYPE_CREATE);
    }

    public void duplicateTaskTypeRejected() {
        increment(DYNAMIC_TASK_TYPE_DUPLICATE);
    }

    public void templateReadinessFailed() {
        increment(TEMPLATE_READINESS_FAILURE);
    }

    public void unsupportedRuntimePreflight() {
        increment(UNSUPPORTED_RUNTIME_PREFLIGHT);
    }

    public void legacyTaskTypeAdapterUsed() {
        increment(LEGACY_TASK_TYPE_ADAPTER);
    }

    public void snapshotRuntimeContractFailed() {
        increment(SNAPSHOT_RUNTIME_CONTRACT_FAILURE);
    }

    private void increment(String name) {
        Counter.builder(name).description("Dynamic task-type rollout event")
                .register(meterRegistry).increment();
    }
}
