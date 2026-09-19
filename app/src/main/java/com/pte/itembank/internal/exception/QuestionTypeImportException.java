package com.pte.itembank.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Invalid or inconsistent task-type data supplied by the UI import flow. */
public class QuestionTypeImportException extends DomainException {

    public QuestionTypeImportException() {
        super(HttpStatus.BAD_REQUEST, "QUESTION_TYPE_IMPORT_INVALID");
    }
}
