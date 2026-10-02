package com.pte.itembank.internal.exception;

import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

public class TaskTypeRuntimeLockedException extends DomainException {
    public TaskTypeRuntimeLockedException(String taskTypeKey) {
        super(HttpStatus.CONFLICT, ItembankConstants.TASK_TYPE_RUNTIME_LOCKED,
                Map.of("taskTypeKey", taskTypeKey, "lockedFields",
                        List.of("screenKey", "section", "runtimeContract")),
                ItembankConstants.TASK_TYPE_RUNTIME_LOCKED_MESSAGE);
    }
}
