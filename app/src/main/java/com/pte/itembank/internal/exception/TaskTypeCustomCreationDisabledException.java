package com.pte.itembank.internal.exception;

import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TaskTypeCustomCreationDisabledException extends DomainException {

    public TaskTypeCustomCreationDisabledException() {
        super(HttpStatus.CONFLICT, ItembankConstants.TASK_TYPE_CUSTOM_CREATION_DISABLED,
                null, ItembankConstants.TASK_TYPE_CUSTOM_CREATION_DISABLED_MESSAGE);
    }
}
