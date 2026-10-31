package com.pte.itembank.internal.exception.tasktype;

import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

public class TaskTypeStandardRuntimeImmutableException extends DomainException {
    public TaskTypeStandardRuntimeImmutableException(String taskTypeKey) {
        super(HttpStatus.CONFLICT, ItembankConstants.TASK_TYPE_STANDARD_RUNTIME_IMMUTABLE,
                Map.of("taskTypeKey", taskTypeKey, "immutableFields",
                        List.of("screenKey", "contractVersion", "section")),
                ItembankConstants.TASK_TYPE_STANDARD_RUNTIME_IMMUTABLE_MESSAGE);
    }
}
