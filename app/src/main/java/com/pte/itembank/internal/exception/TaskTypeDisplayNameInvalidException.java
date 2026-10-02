package com.pte.itembank.internal.exception;

import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** User-facing validation error for an empty or overlong display name. */
public class TaskTypeDisplayNameInvalidException extends DomainException {
    public TaskTypeDisplayNameInvalidException() {
        super(HttpStatus.BAD_REQUEST, ItembankConstants.TASK_TYPE_DISPLAY_NAME_INVALID,
                null, ItembankConstants.TASK_TYPE_DISPLAY_NAME_INVALID_MESSAGE);
    }
}
