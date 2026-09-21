package com.pte.itembank.internal.exception;

import com.pte.itembank.internal.constant.TaskRuntimeProfileConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TaskRuntimeProfileException extends DomainException {

    public TaskRuntimeProfileException(String code) {
        super(HttpStatus.CONFLICT, code);
    }

    public static TaskRuntimeProfileException notFound() {
        return new TaskRuntimeProfileException(TaskRuntimeProfileConstants.PROFILE_NOT_FOUND);
    }

    public static TaskRuntimeProfileException notAllowed() {
        return new TaskRuntimeProfileException(TaskRuntimeProfileConstants.PROFILE_NOT_ALLOWED);
    }

    public static TaskRuntimeProfileException notActive() {
        return new TaskRuntimeProfileException(TaskRuntimeProfileConstants.PROFILE_NOT_ACTIVE);
    }
}
