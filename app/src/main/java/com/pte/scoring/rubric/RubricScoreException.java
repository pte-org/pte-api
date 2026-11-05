package com.pte.scoring.rubric;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Trait scores or rubric parameters that the rubric cannot score. Raised instead of clamping or rounding, so a
 * malformed AI answer or an invalid parameter set fails closed rather than producing a plausible wrong score.
 */
public class RubricScoreException extends DomainException {

    public RubricScoreException(String code) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code);
    }
}
