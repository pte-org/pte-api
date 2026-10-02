package com.pte.itembank.internal.exception;

import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TaskTypeKeyAlreadyUsedException extends DomainException {
    public TaskTypeKeyAlreadyUsedException() {
        super(HttpStatus.CONFLICT, ItembankConstants.TASK_TYPE_KEY_ALREADY_USED,
                null, ItembankConstants.TASK_TYPE_KEY_ALREADY_USED_MESSAGE);
    }
}
