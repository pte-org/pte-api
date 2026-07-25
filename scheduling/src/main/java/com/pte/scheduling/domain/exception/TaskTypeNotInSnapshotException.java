package com.pte.scheduling.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.scheduling.constant.SchedulingConstants;
import org.springframework.http.HttpStatus;

/** Composition referenced a task type the session's snapshot doesn't contain. */
public class TaskTypeNotInSnapshotException extends DomainException {

    public TaskTypeNotInSnapshotException() {
        super(HttpStatus.BAD_REQUEST, SchedulingConstants.TASK_TYPE_NOT_IN_SNAPSHOT);
    }
}
