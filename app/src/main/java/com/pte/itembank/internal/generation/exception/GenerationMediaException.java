package com.pte.itembank.internal.generation.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Audio or an image could not be produced or stored for an otherwise accepted draft. */
public class GenerationMediaException extends DomainException {

    public GenerationMediaException(String code) {
        super(HttpStatus.BAD_GATEWAY, code);
    }

    /** {@code note} is a short, content-free explanation (for example the measured and the expected length). */
    public GenerationMediaException(String code, String note) {
        super(HttpStatus.BAD_GATEWAY, code, null, note);
    }
}
