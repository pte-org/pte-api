package com.pte.itembank.internal.generation.exception;

import com.pte.itembank.internal.generation.constant.BatchGenerationConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Unknown batch, or another writer's batch the caller may not see (its existence is not told). */
public class GenerationBatchNotFoundException extends DomainException {

    public GenerationBatchNotFoundException() {
        super(HttpStatus.NOT_FOUND, BatchGenerationConstants.GENERATION_BATCH_NOT_FOUND, null,
                BatchGenerationConstants.GENERATION_BATCH_NOT_FOUND_MESSAGE);
    }
}
