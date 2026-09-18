package com.pte.itembank.dto.request;

import com.pte.itembank.internal.constant.ItembankConstants;
import jakarta.validation.constraints.NotBlank;

public record OptionRequest(
        @NotBlank(message = ItembankConstants.OPTION_TEXT_REQUIRED) String text,
        boolean correct,
        int orderIndex,
        Integer blankIndex,
        Integer correctGapIndex) {

    public OptionRequest(String text, boolean correct, int orderIndex) {
        this(text, correct, orderIndex, null, null);
    }
}
