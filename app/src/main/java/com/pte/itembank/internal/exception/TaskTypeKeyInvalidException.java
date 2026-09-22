package com.pte.itembank.internal.exception;

import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TaskTypeKeyInvalidException extends DomainException {
    public TaskTypeKeyInvalidException() {
        super(HttpStatus.BAD_REQUEST, ItembankConstants.TASK_TYPE_KEY_INVALID,
                null, ItembankConstants.TASK_TYPE_KEY_INVALID_MESSAGE);
    }
}
