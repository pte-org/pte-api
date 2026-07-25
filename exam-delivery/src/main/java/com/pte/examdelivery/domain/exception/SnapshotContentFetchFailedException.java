package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

/** The guarded call to authoring for full snapshot content failed. */
public class SnapshotContentFetchFailedException extends DomainException {

    public SnapshotContentFetchFailedException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, ExamDeliveryConstants.SNAPSHOT_CONTENT_FETCH_FAILED);
    }
}
