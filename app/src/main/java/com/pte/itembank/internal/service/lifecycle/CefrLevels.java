package com.pte.itembank.internal.service.lifecycle;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.QuestionValidationException;

/** Reads the CEFR level of a request, turning an unknown name into a validation error. */
final class CefrLevels {

    private CefrLevels() {
    }

    static CefrLevel parse(String value) {
        try {
            return CefrLevel.parseOrNull(value);
        } catch (IllegalArgumentException ex) {
            throw new QuestionValidationException(ItembankConstants.INVALID_CEFR_LEVEL);
        }
    }
}
