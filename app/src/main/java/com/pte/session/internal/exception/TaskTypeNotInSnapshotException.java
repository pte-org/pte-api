package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Composition referenced a task type the session's snapshot doesn't contain. */
public class TaskTypeNotInSnapshotException extends DomainException {

    public TaskTypeNotInSnapshotException() {
        super(HttpStatus.BAD_REQUEST, SessionConstants.TASK_TYPE_NOT_IN_SNAPSHOT);
    }
}
