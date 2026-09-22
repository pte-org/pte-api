package com.pte.itembank.internal.exception;

import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TaskTypeCapabilityNotFoundException extends DomainException {
    public TaskTypeCapabilityNotFoundException() {
        super(HttpStatus.CONFLICT, ItembankConstants.TASK_TYPE_CAPABILITY_NOT_FOUND,
                null, ItembankConstants.TASK_TYPE_CAPABILITY_NOT_FOUND_MESSAGE);
    }
}
