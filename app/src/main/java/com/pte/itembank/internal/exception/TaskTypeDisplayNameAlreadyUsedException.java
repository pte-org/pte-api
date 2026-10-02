package com.pte.itembank.internal.exception;

import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TaskTypeDisplayNameAlreadyUsedException extends DomainException {
    public TaskTypeDisplayNameAlreadyUsedException() {
        super(HttpStatus.CONFLICT, ItembankConstants.TASK_TYPE_DISPLAY_NAME_ALREADY_USED,
                null, ItembankConstants.TASK_TYPE_DISPLAY_NAME_ALREADY_USED_MESSAGE);
    }
}
