package com.pte.itembank.internal.service.tasktype;

import com.pte.itembank.TaskRuntimeContractService;
import com.pte.itembank.internal.exception.tasktype.TaskTypeCapabilityNotFoundException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeContractResolverTest {

    @Test
    void anUnknownContractLookupFailureIsReportedAsAnUnknownCapability() {
        TaskRuntimeContractService contracts = mock(TaskRuntimeContractService.class);
        when(contracts.resolveActive("missing-screen", 9)).thenThrow(new IllegalStateException("no such contract"));

        assertThatThrownBy(() -> new RuntimeContractResolver(contracts).resolveActive("missing-screen", 9))
                .isInstanceOf(TaskTypeCapabilityNotFoundException.class);
    }
}
