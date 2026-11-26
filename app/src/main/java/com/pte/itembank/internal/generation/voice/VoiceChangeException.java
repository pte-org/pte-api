package com.pte.itembank.internal.generation.voice;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Changing the voice of a question was refused; the code says why. */
public class VoiceChangeException extends DomainException {

    public VoiceChangeException(HttpStatus status, String code) {
        super(status, code);
    }
}
