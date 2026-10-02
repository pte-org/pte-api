package com.pte.assessment.internal.exception;

import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Publishing is blocked when a snapshot item cannot carry a trusted runtime contract. */
public class SnapshotRuntimeContractException extends DomainException {

    private final String diagnosticMessage;

    public SnapshotRuntimeContractException(String diagnosticMessage) {
        super(HttpStatus.CONFLICT, AssessmentConstants.SNAPSHOT_RUNTIME_CONTRACT_INVALID, null,
                AssessmentConstants.SNAPSHOT_RUNTIME_CONTRACT_MESSAGE,
                AssessmentConstants.SNAPSHOT_RUNTIME_CONTRACT_INVALID);
        this.diagnosticMessage = diagnosticMessage;
    }

    public String diagnosticMessage() {
        return diagnosticMessage;
    }
}
